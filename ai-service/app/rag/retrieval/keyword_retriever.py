import asyncio
import logging
import re
import time

from sqlalchemy import func, select, text

from app.rag.models import RagChunk
from app.rag.retriever import RetrievalCandidate, RetrievalQuery

logger = logging.getLogger(__name__)

_TOKEN_RE = re.compile(r"[A-Za-z0-9]+|[\u4e00-\u9fff]+")

# 低区分度词判定阈值：ndoc >= 60% 语料 chunk 的词视为停用词近似，直接丢弃。
STOPWORD_DOC_FRACTION = 0.6
# 过滤后最多保留的查询词数（按 IDF 从高到低取前 N 个）。
MAX_QUERY_TERMS = 6
# KB 级 IDF 统计缓存 TTL（秒）：语料变化慢，短 TTL 即可平衡新鲜度与查询开销。
IDF_CACHE_TTL_SECONDS = 300


def tokenize(text: str) -> list[str]:
    """把自然语言查询拆成词元（英文单词 + 连续中文串）。"""
    return _TOKEN_RE.findall(text)


def build_tsquery(tokens: list[str]):
    """OR 语义 tsquery：保留 ts_rank_cd 的 cover density 归一化排序。"""
    return func.to_tsquery("simple", " | ".join(tokens))


class KeywordRetriever:
    """IDF 预过滤 + FTS 排序的关键词检索器。

    背景：裸 OR tsquery 的问题在于自然语言查询里的高频词（the/a/of/how…）
    会命中大量无关 chunk，经 ts_rank_cd 放大后把噪声注入 RRF 融合，
    实测 hybrid 反而比单路 dense 差 12.9%（R@5 0.500 vs 0.5738）。

    做法（可解释的「低区分度词截断」近似）：
    1. 用 PostgreSQL ts_stat 取 KB 级词统计（ndoc/nentry），按查询词元查 IDF；
    2. 丢弃 ndoc >= 60% 语料的词（停用词近似）与语料中不存在的词；
    3. 剩余词按 IDF 降序最多保留 6 个；
    4. 全部被过滤 → 返回空列表，放弃关键词通道（hybrid 退化为 dense，
       避免噪声注入）；
    5. 排序仍用 ts_rank_cd（在 SQL 之前完成过滤）。

    为什么不实现完整 BM25：PostgreSQL 的 tsvector 不保留词频元数据，
    候选级 tf 需要额外建词频表并维护；本实现用 ts_stat 的 ndoc 做
    IDF 预过滤 + ts_rank_cd 打分，是 BM25 的可解释近似，而非伪造的 BM25。
    """

    def __init__(self, session_factory) -> None:
        self.session_factory = session_factory
        # kb_id -> (loaded_at_monotonic, total_chunks, {word: ndoc})
        self._stats_cache: dict[str, tuple[float, int, dict[str, int]]] = {}
        self._stats_lock = asyncio.Lock()

    async def retrieve(
        self,
        request: RetrievalQuery,
        top_k: int = 20,
    ) -> list[RetrievalCandidate]:
        tokens = [token.lower() for token in tokenize(request.text)]
        if not tokens:
            return []

        total_chunks, word_ndoc = await self._idf_stats(request.knowledge_base_id)
        threshold = total_chunks * STOPWORD_DOC_FRACTION

        # IDF 预过滤：停用词近似 + 语料中不存在的词（OR 语义下无贡献）
        kept: list[tuple[int, str]] = []
        for token in tokens:
            ndoc = word_ndoc.get(token)
            if ndoc is None or ndoc >= threshold:
                continue
            kept.append((ndoc, token))

        if not kept:
            logger.debug(
                "keyword channel abandoned after IDF filter, kb=%s query=%r",
                request.knowledge_base_id,
                request.text,
            )
            return []

        # ndoc 升序 = IDF 降序，保留区分度最高的 MAX_QUERY_TERMS 个词
        kept.sort(key=lambda item: item[0])
        kept = kept[:MAX_QUERY_TERMS]
        query_vector = build_tsquery([token for _, token in kept])

        rank = func.ts_rank_cd(RagChunk.search_vector, query_vector)
        statement = (
            select(RagChunk, rank.label("keyword_score"))
            .where(RagChunk.knowledge_base_id == request.knowledge_base_id)
            .where(RagChunk.search_vector.op("@@")(query_vector))
            .order_by(rank.desc(), RagChunk.id)
            .limit(top_k)
        )
        async with self.session_factory() as session:
            rows = (await session.execute(statement)).all()

        return [
            RetrievalCandidate(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                content=chunk.content,
                source=chunk.metadata_.get("source"),
                file_name=chunk.metadata_.get("file_name"),
                chunk_index=chunk.chunk_index,
                char_start=chunk.char_start,
                char_end=chunk.char_end,
                keyword_score=float(score),
            )
            for chunk, score in rows
        ]

    async def _idf_stats(self, kb_id: str) -> tuple[int, dict[str, int]]:
        """加载 KB 级词统计（带 TTL 缓存）；失败时放弃关键词通道。"""
        now = time.monotonic()
        cached = self._stats_cache.get(kb_id)
        if cached is not None and now - cached[0] < IDF_CACHE_TTL_SECONDS:
            return cached[1], cached[2]

        if not re.fullmatch(r"[A-Za-z0-9_-]+", kb_id):
            raise ValueError(f"invalid knowledge_base_id: {kb_id!r}")

        async with self._stats_lock:
            cached = self._stats_cache.get(kb_id)
            if cached is not None and now - cached[0] < IDF_CACHE_TTL_SECONDS:
                return cached[1], cached[2]
            total, word_ndoc = await self._load_idf_stats(kb_id)
            self._stats_cache[kb_id] = (time.monotonic(), total, word_ndoc)
            return total, word_ndoc

    async def _load_idf_stats(self, kb_id: str) -> tuple[int, dict[str, int]]:
        # ts_stat 的参数是 SQL 字符串而非绑定参数；kb_id 已在上层校验为
        # [A-Za-z0-9_-]+，此处再转义单引号双保险。
        escaped_kb = kb_id.replace("'", "''")
        async with self.session_factory() as session:
            total = (
                await session.execute(
                    select(func.count())
                    .select_from(RagChunk)
                    .where(RagChunk.knowledge_base_id == kb_id)
                )
            ).scalar_one()
            rows = (
                await session.execute(
                    text(
                        "SELECT word, ndoc FROM ts_stat("
                        f"'SELECT search_vector FROM rag_chunk "
                        f"WHERE knowledge_base_id = ''{escaped_kb}'''"
                        ")"
                    )
                )
            ).all()
        word_ndoc = {word: ndoc for word, ndoc in rows}
        logger.info(
            "idf stats loaded, kb=%s total_chunks=%d words=%d",
            kb_id,
            total,
            len(word_ndoc),
        )
        return total, word_ndoc

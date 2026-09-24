from uuid import uuid4

import pytest

from app.rag.retrieval import dense_retriever as dense_module
from app.rag.retrieval.dense_retriever import DenseRetriever
from app.rag.retrieval.keyword_retriever import KeywordRetriever
from app.rag.models import RagChunk
from app.rag.retriever import RetrievalCandidate, RetrievalQuery
from app.rag.vector_store import SearchMode


class _SessionContext:
    def __init__(self, session, events):
        self.session = session
        self.events = events

    async def __aenter__(self):
        self.events.append("opened")
        return self.session

    async def __aexit__(self, *args):
        self.events.append("closed")


class _SessionFactory:
    def __init__(self, session):
        self.session = session
        self.events = []

    def __call__(self):
        return _SessionContext(self.session, self.events)


class _Embedder:
    async def embed_query(self, query):
        self.query = query
        return [0.25] * 1024


def _candidate() -> RetrievalCandidate:
    return RetrievalCandidate(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content="dense result",
        source="docs/dense.md",
        file_name="dense.md",
        chunk_index=0,
        char_start=0,
        char_end=12,
        vector_score=0.75,
    )


@pytest.mark.anyio
async def test_dense_retriever_embeds_query_and_uses_scoped_vector_search(monkeypatch) -> None:
    result = _candidate()
    created_stores = []

    class _Store:
        def __init__(self, session):
            self.session = session
            created_stores.append(self)

        async def search(self, **kwargs):
            self.kwargs = kwargs
            return [result]

    monkeypatch.setattr(dense_module, "PgVectorStore", _Store)
    embedder = _Embedder()
    factory = _SessionFactory(object())
    request = RetrievalQuery(text="dense query", knowledge_base_id="kb-1")

    results = await DenseRetriever(embedder, factory).retrieve(request, top_k=7)

    assert results == [result]
    assert embedder.query == "dense query"
    assert created_stores[0].kwargs == {
        "embedding": [0.25] * 1024,
        "knowledge_base_id": "kb-1",
        "top_k": 7,
    }
    assert factory.events == ["opened", "closed"]


@pytest.mark.anyio
async def test_dense_retriever_owns_vector_benchmark_operations(monkeypatch) -> None:
    result = _candidate()

    class _Store:
        def __init__(self, session):
            self.session = session

        async def has_hnsw_index(self):
            return True

        async def benchmark_search(self, **kwargs):
            self.kwargs = kwargs
            return [result], 4.5

    monkeypatch.setattr(dense_module, "PgVectorStore", _Store)
    embedder = _Embedder()
    factory = _SessionFactory(object())
    dense = DenseRetriever(embedder, factory)

    embedding = await dense.embed_query("benchmark")
    has_index = await dense.has_hnsw_index()
    results, latency_ms = await dense.benchmark_by_embedding(
        embedding=embedding,
        knowledge_base_id="kb-2",
        top_k=10,
        mode=SearchMode.HNSW,
    )

    assert embedder.query == "benchmark"
    assert has_index is True
    assert results == [result]
    assert latency_ms == 4.5
    assert factory.events == ["opened", "closed", "opened", "closed"]


class _Rows:
    def __init__(self, rows):
        self.rows = rows

    def all(self):
        return self.rows

    def scalar_one(self):
        return self.rows[0][0]


class _StatsAwareSession:
    """按 SQL 形状分发返回值的假 session：

    - ts_stat 文本查询 → 词统计行 [(word, ndoc), ...]
    - count(*) 查询     → [(total,)]
    - FTS 查询          → [(chunk, score), ...]，并记录语句供断言
    """

    def __init__(self, stats_rows, fts_rows, total=1000):
        self.stats_rows = stats_rows
        self.fts_rows = fts_rows
        self.total = total
        self.fts_statements = []
        self.stats_calls = 0

    async def execute(self, statement):
        sql = str(statement)
        if "ts_stat" in sql:
            self.stats_calls += 1
            return _Rows(self.stats_rows)
        if "count(" in sql:
            return _Rows([(self.total,)])
        self.fts_statements.append(statement)
        return _Rows(self.fts_rows)


def _keyword_chunk() -> RagChunk:
    return RagChunk(
        id=uuid4(),
        document_id=uuid4(),
        knowledge_base_id="kb-1",
        chunk_index=3,
        content="keyword result",
        char_start=4,
        char_end=18,
        metadata_={"source": "docs/keyword.md", "file_name": "keyword.md"},
    )


@pytest.mark.anyio
async def test_keyword_retriever_filters_low_idf_words_and_maps_fields() -> None:
    chunk = _keyword_chunk()
    # 语料 1000 chunks：threshold = 600；the(600)/and(700) 为低区分度词应被丢弃
    session = _StatsAwareSession(
        stats_rows=[("keyword", 10), ("query", 12), ("the", 600), ("and", 700)],
        fts_rows=[(chunk, 0.6)],
        total=1000,
    )
    factory = _SessionFactory(session)
    request = RetrievalQuery(text="keyword query the and", knowledge_base_id="kb-1")

    results = await KeywordRetriever(factory).retrieve(request, top_k=9)

    statement = session.fts_statements[0]
    params = statement.compile().params
    # IDF 过滤后仅剩高区分度词，以 OR 语义进入 tsquery
    assert "keyword | query" in params.values()
    assert "kb-1" in params.values()
    assert 9 in params.values()
    assert "@@" in str(statement)
    assert results == [
        RetrievalCandidate(
            chunk_id=chunk.id,
            document_id=chunk.document_id,
            content="keyword result",
            source="docs/keyword.md",
            file_name="keyword.md",
            chunk_index=3,
            char_start=4,
            char_end=18,
            keyword_score=0.6,
        )
    ]
    # 统计加载 + FTS 检索各开一次会话
    assert factory.events == ["opened", "closed", "opened", "closed"]


@pytest.mark.anyio
async def test_keyword_retriever_keeps_at_most_six_terms_by_idf() -> None:
    # 8 个可区分词，ndoc 越小 IDF 越高；应保留 ndoc 最小的 6 个
    tokens = [f"t{i}" for i in range(1, 9)]
    session = _StatsAwareSession(
        stats_rows=[(t, i) for i, t in enumerate(tokens, start=1)],
        fts_rows=[],
        total=1000,
    )
    request = RetrievalQuery(text=" ".join(tokens), knowledge_base_id="kb-1")

    await KeywordRetriever(_SessionFactory(session)).retrieve(request, top_k=5)

    params = session.fts_statements[0].compile().params
    assert "t1 | t2 | t3 | t4 | t5 | t6" in params.values()
    assert not any("t7" in str(p) for p in params.values())


@pytest.mark.anyio
async def test_keyword_retriever_abandons_channel_when_all_words_filtered() -> None:
    # 全部是低区分度词：放弃关键词通道（不执行 FTS 查询，直接空结果）
    session = _StatsAwareSession(
        stats_rows=[("the", 900), ("of", 950)],
        fts_rows=[(None, 1.0)],
        total=1000,
    )
    request = RetrievalQuery(text="the of", knowledge_base_id="kb-1")

    results = await KeywordRetriever(_SessionFactory(session)).retrieve(request, top_k=5)

    assert results == []
    assert session.fts_statements == []


@pytest.mark.anyio
async def test_keyword_retriever_drops_words_absent_from_corpus() -> None:
    # 语料中不存在的词在 OR 语义下无贡献，直接丢弃
    session = _StatsAwareSession(
        stats_rows=[("known", 5)],
        fts_rows=[],
        total=1000,
    )
    request = RetrievalQuery(text="known absentword", knowledge_base_id="kb-1")

    await KeywordRetriever(_SessionFactory(session)).retrieve(request, top_k=5)

    params = session.fts_statements[0].compile().params
    assert "known" in params.values()


@pytest.mark.anyio
async def test_keyword_retriever_caches_idf_stats_per_kb() -> None:
    # ts_stat 全表扫描代价高：同一 KB 的统计在 TTL 内只加载一次
    session = _StatsAwareSession(
        stats_rows=[("keyword", 10), ("query", 12)],
        fts_rows=[],
        total=1000,
    )
    retriever = KeywordRetriever(_SessionFactory(session))
    request = RetrievalQuery(text="keyword query", knowledge_base_id="kb-1")

    await retriever.retrieve(request, top_k=5)
    await retriever.retrieve(request, top_k=5)

    assert session.stats_calls == 1
    assert len(session.fts_statements) == 2

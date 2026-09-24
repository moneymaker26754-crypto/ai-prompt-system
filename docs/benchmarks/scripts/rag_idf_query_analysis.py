"""p1 查询集 × IDF 过滤的行为分析（不调用 Ollama，纯 DB 统计）。

回答：79 条 p1 查询里有多少条在 IDF 过滤后仍有关键词通道结果、
平均保留几个查询词、被丢弃的停用词分布——用于解释 hybrid 行为变化。
"""
import asyncio
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ai-service"))

from app.database import create_async_engine, create_session_factory  # noqa: E402
from app.rag.retrieval.keyword_retriever import (  # noqa: E402
    MAX_QUERY_TERMS,
    STOPWORD_DOC_FRACTION,
    KeywordRetriever,
    tokenize,
)

DB = "postgresql+asyncpg://rag:rag_password@localhost:5433/rag"
DATASET = Path(r"D:\Code\java_program\ai-prompt-benchmark\rag-eval\datasets\p1.jsonl")


async def main() -> None:
    engine = create_async_engine(DB)
    session_factory = create_session_factory(engine)
    retriever = KeywordRetriever(session_factory)
    try:
        queries = []
        for line in DATASET.read_text(encoding="utf-8").splitlines():
            if line.strip():
                queries.append(json.loads(line)["query"])

        kept_counts = []
        dropped_stopwords: dict[str, int] = {}
        empty_queries = 0
        per_kb_stats = {}
        for q in queries:
            tokens = [t.lower() for t in tokenize(q)]
            kb_id = "kb-main"
            total, word_ndoc = await retriever._idf_stats(kb_id)
            threshold = total * STOPWORD_DOC_FRACTION
            kept = 0
            dropped = []
            for token in tokens:
                ndoc = word_ndoc.get(token)
                if ndoc is None or ndoc >= threshold:
                    dropped.append(token)
                    if ndoc is not None:
                        dropped_stopwords[token] = dropped_stopwords.get(token, 0) + 1
                else:
                    kept += 1
            kept = min(kept, MAX_QUERY_TERMS)
            kept_counts.append(kept)
            if kept == 0:
                empty_queries += 1
            per_kb_stats.setdefault(kb_id, {})

        summary = {
            "queries": len(queries),
            "kb_main_total_chunks": None,
            "stopword_threshold_ndoc": None,
            "empty_keyword_channel_queries": empty_queries,
            "empty_ratio": round(empty_queries / len(queries), 4),
            "mean_kept_terms": round(sum(kept_counts) / len(kept_counts), 2),
            "queries_with_max_terms": sum(1 for k in kept_counts if k == MAX_QUERY_TERMS),
            "top_dropped_words": dict(
                sorted(dropped_stopwords.items(), key=lambda kv: -kv[1])[:15]
            ),
        }
        total, word_ndoc = await retriever._idf_stats("kb-main")
        summary["kb_main_total_chunks"] = total
        summary["stopword_threshold_ndoc"] = round(total * STOPWORD_DOC_FRACTION, 1)
        out = Path(__file__).resolve().parents[1] / "raw" / "rag_idf_query_analysis.json"
        out.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        print("wrote", out)
    finally:
        await engine.dispose()


if __name__ == "__main__":
    asyncio.run(main())

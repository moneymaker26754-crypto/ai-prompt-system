"""IDF 关键词检索真实库冒烟：验证 ts_stat SQL 与过滤行为。"""
import asyncio
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "ai-service"))

from app.database import create_async_engine, create_session_factory  # noqa: E402
from app.rag.retrieval.keyword_retriever import KeywordRetriever, tokenize  # noqa: E402
from app.rag.retriever import RetrievalQuery  # noqa: E402

DB = "postgresql+asyncpg://rag:rag_password@localhost:5433/rag"
QUERIES = [
    "What is the impact of Bitcoin halving on market cycles?",
    "How does the of the and a to work with crypto derivatives?",
    "Explain the role of market makers in liquidity provision",
]


async def main() -> None:
    engine = create_async_engine(DB)
    session_factory = create_session_factory(engine)
    retriever = KeywordRetriever(session_factory)
    try:
        for q in QUERIES:
            tokens = tokenize(q)
            results = await retriever.retrieve(
                RetrievalQuery(text=q, knowledge_base_id="kb-main"), top_k=5
            )
            print("query:", q)
            print("  tokens:", tokens)
            print("  keyword hits:", len(results))
            for r in results[:3]:
                print("    -", r.source, r.chunk_index, round(r.keyword_score or 0, 4))
    finally:
        await engine.dispose()


if __name__ == "__main__":
    asyncio.run(main())

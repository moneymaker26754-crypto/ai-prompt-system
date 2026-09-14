import asyncio

from app.rag.retrieval.fusion import reciprocal_rank_fusion
from app.rag.retriever import RetrievalCandidate, RetrievalQuery, Retriever


class HybridRetriever:
    def __init__(self, dense_retriever: Retriever, keyword_retriever: Retriever) -> None:
        self.dense_retriever = dense_retriever
        self.keyword_retriever = keyword_retriever

    async def retrieve(
        self,
        request: RetrievalQuery,
        top_k: int = 20,
    ) -> list[RetrievalCandidate]:
        tasks = (
            asyncio.create_task(self.dense_retriever.retrieve(request, top_k)),
            asyncio.create_task(self.keyword_retriever.retrieve(request, top_k)),
        )
        try:
            dense_results, keyword_results = await asyncio.gather(*tasks)
        except BaseException:
            for task in tasks:
                if not task.done():
                    task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            raise
        return reciprocal_rank_fusion([dense_results, keyword_results])[:top_k]

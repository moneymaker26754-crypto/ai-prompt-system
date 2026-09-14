from app.core.exceptions import ModelUnavailableError
from app.rag.retriever import RetrievalCandidate, RetrievalQuery, Retriever


class RetrievalService:
    def __init__(self, hybrid_retriever: Retriever, reranker=None) -> None:
        self.hybrid_retriever = hybrid_retriever
        self.reranker = reranker

    async def retrieve(
        self,
        query: str,
        knowledge_base_id: str,
        retrieve_top_k: int = 20,
        final_top_k: int = 5,
        rerank: bool = True,
    ) -> list[RetrievalCandidate]:
        candidates = await self.hybrid_retriever.retrieve(
            RetrievalQuery(text=query, knowledge_base_id=knowledge_base_id),
            top_k=max(retrieve_top_k, final_top_k),
        )
        if not rerank:
            return candidates[:final_top_k]
        if self.reranker is None:
            raise ModelUnavailableError("Reranker model is unavailable")
        return await self.reranker.rerank(
            query=query,
            candidates=candidates,
            top_k=final_top_k,
        )

from app.core.exceptions import ModelUnavailableError
from app.rag.vector_store import SearchMode


class RetrievalService:

    def __init__(
            self,
            embedder,
            vector_store,
            reranker=None,
    ):
        self.embedder = embedder
        self.vector_store = vector_store
        self.reranker = reranker

    async def retrieve(
            self,
            query: str,
            knowledge_base_id: str,
            retrieve_top_k: int = 20,
            final_top_k: int = 10,
            rerank: bool = False,
    ) -> list:

        query_embedding = await self.embed_query(query)

        candidates = await self.retrieve_by_embedding(
            embedding=query_embedding,
            knowledge_base_id=knowledge_base_id,
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

    async def embed_query(self, query: str) -> list[float]:
        return await self.embedder.embed_query(query)

    async def retrieve_by_embedding(
            self,
            embedding: list[float],
            knowledge_base_id: str,
            top_k: int,
            mode: SearchMode = SearchMode.PLANNER,
    ) -> list:
        search_args = {
            "embedding": embedding,
            "knowledge_base_id": knowledge_base_id,
            "top_k": top_k,
        }
        if mode is not SearchMode.PLANNER:
            search_args["mode"] = mode
        return await self.vector_store.search(**search_args)

    async def has_hnsw_index(self) -> bool:
        return await self.vector_store.has_hnsw_index()

    async def benchmark_by_embedding(
            self,
            embedding: list[float],
            knowledge_base_id: str,
            top_k: int,
            mode: SearchMode,
    ) -> tuple[list, float]:
        return await self.vector_store.benchmark_search(
            embedding=embedding,
            knowledge_base_id=knowledge_base_id,
            top_k=top_k,
            mode=mode,
        )

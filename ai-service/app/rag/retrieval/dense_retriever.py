from app.rag.retriever import RetrievalCandidate, RetrievalQuery
from app.rag.vector_store import PgVectorStore, SearchMode


class DenseRetriever:
    def __init__(self, embedder, session_factory) -> None:
        self.embedder = embedder
        self.session_factory = session_factory

    async def retrieve(
        self,
        request: RetrievalQuery,
        top_k: int = 20,
    ) -> list[RetrievalCandidate]:
        embedding = await self.embedder.embed_query(request.text)
        async with self.session_factory() as session:
            return await PgVectorStore(session).search(
                embedding=embedding,
                knowledge_base_id=request.knowledge_base_id,
                top_k=top_k,
            )

    async def embed_query(self, query: str) -> list[float]:
        return await self.embedder.embed_query(query)

    async def has_hnsw_index(self) -> bool:
        async with self.session_factory() as session:
            return await PgVectorStore(session).has_hnsw_index()

    async def benchmark_by_embedding(
        self,
        embedding: list[float],
        knowledge_base_id: str,
        top_k: int,
        mode: SearchMode,
    ) -> tuple[list[RetrievalCandidate], float]:
        async with self.session_factory() as session:
            return await PgVectorStore(session).benchmark_search(
                embedding=embedding,
                knowledge_base_id=knowledge_base_id,
                top_k=top_k,
                mode=mode,
            )

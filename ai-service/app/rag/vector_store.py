from dataclasses import dataclass
from enum import StrEnum
from time import perf_counter_ns
from uuid import UUID

from sqlalchemy import select, text

from app.rag.models import RagChunk


class SearchMode(StrEnum):
    PLANNER = "planner"
    EXACT = "exact"
    HNSW = "hnsw"


@dataclass
class SearchResult:
    chunk_id: UUID
    document_id: UUID
    content: str
    source: str | None
    file_name: str | None
    chunk_index: int
    char_start: int | None
    char_end: int | None
    vector_score: float
    rerank_score: float | None = None


class PgVectorStore:

    def __init__(self, session):
        self.session = session

    async def search(
            self,
            embedding: list[float],
            knowledge_base_id: str,
            top_k: int = 20,
            mode: SearchMode = SearchMode.PLANNER,
    ) -> list[SearchResult]:
        await self._configure_search_mode(mode)
        statement = self._build_search_statement(
            embedding,
            knowledge_base_id,
            top_k,
        )
        rows = (await self.session.execute(statement)).all()
        return self._map_search_results(rows)

    async def benchmark_search(
            self,
            embedding: list[float],
            knowledge_base_id: str,
            top_k: int,
            mode: SearchMode,
    ) -> tuple[list[SearchResult], float]:
        await self._configure_search_mode(mode)
        statement = self._build_search_statement(
            embedding,
            knowledge_base_id,
            top_k,
        )

        started_at = perf_counter_ns()
        result = await self.session.execute(statement)
        latency_ms = (perf_counter_ns() - started_at) / 1_000_000

        return self._map_search_results(result.all()), latency_ms

    async def has_hnsw_index(self) -> bool:
        statement = text(
            """
            SELECT EXISTS (
                SELECT 1
                FROM pg_class AS index_class
                JOIN pg_namespace AS namespace
                  ON namespace.oid = index_class.relnamespace
                JOIN pg_index AS index_metadata
                  ON index_metadata.indexrelid = index_class.oid
                JOIN pg_class AS table_class
                  ON table_class.oid = index_metadata.indrelid
                WHERE namespace.nspname = current_schema()
                  AND table_class.relname = 'rag_chunk'
                  AND index_class.relname = 'rag_chunk_embedding_hnsw'
                  AND pg_get_indexdef(index_class.oid) ILIKE '%USING hnsw%'
                  AND pg_get_indexdef(index_class.oid) ILIKE '%vector_cosine_ops%'
                  AND COALESCE(
                        index_class.reloptions,
                        ARRAY[]::text[]
                      ) @> ARRAY['m=16', 'ef_construction=64']
            )
            """
        )
        return bool((await self.session.execute(statement)).scalar_one())

    async def _configure_search_mode(self, mode: SearchMode) -> None:
        if mode is SearchMode.PLANNER:
            return

        await self.session.execute(text("SET LOCAL enable_indexscan = on"))
        await self.session.execute(text("SET LOCAL enable_seqscan = on"))

        if mode is SearchMode.EXACT:
            await self.session.execute(text("SET LOCAL enable_indexscan = off"))
        elif mode is SearchMode.HNSW:
            await self.session.execute(text("SET LOCAL enable_seqscan = off"))
            await self.session.execute(text("SET LOCAL hnsw.ef_search = 40"))

    @staticmethod
    def _build_search_statement(
            embedding: list[float],
            knowledge_base_id: str,
            top_k: int,
    ):
        distance = RagChunk.embedding.cosine_distance(embedding)
        return (
            select(RagChunk, distance.label("distance"))
            .where(RagChunk.embedding.is_not(None))
            .where(RagChunk.knowledge_base_id == knowledge_base_id)
            .order_by(distance)
            .limit(top_k)
        )

    @staticmethod
    def _map_search_results(rows) -> list[SearchResult]:
        return [
            SearchResult(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                content=chunk.content,
                source=chunk.metadata_.get("source"),
                file_name=chunk.metadata_.get("file_name"),
                chunk_index=chunk.chunk_index,
                char_start=chunk.char_start,
                char_end=chunk.char_end,
                vector_score=1.0 - float(distance),
            )
            for chunk, distance in rows
        ]

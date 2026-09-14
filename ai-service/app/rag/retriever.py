from dataclasses import dataclass
from typing import Protocol
from uuid import UUID


@dataclass(frozen=True)
class RetrievalQuery:
    text: str
    knowledge_base_id: str


@dataclass(frozen=True)
class RetrievalCandidate:
    chunk_id: UUID
    document_id: UUID
    content: str
    source: str | None
    file_name: str | None
    chunk_index: int
    char_start: int | None
    char_end: int | None
    vector_score: float | None = None
    keyword_score: float | None = None
    fusion_score: float | None = None
    rerank_score: float | None = None


class Retriever(Protocol):
    async def retrieve(
            self,
            query: str,
            retrieve_top_k: int = 20,
            final_top_k: int = 5,
            mode: str = "hybrid",
            filters=None,
            rerank: bool = True,
    ) -> list[RetrievalCandidate]:
        ...

from sqlalchemy import func, select

from app.rag.models import RagChunk
from app.rag.retriever import RetrievalCandidate, RetrievalQuery


class KeywordRetriever:
    def __init__(self, session_factory) -> None:
        self.session_factory = session_factory

    async def retrieve(
        self,
        request: RetrievalQuery,
        top_k: int = 20,
    ) -> list[RetrievalCandidate]:
        query_vector = func.websearch_to_tsquery("simple", request.text)
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

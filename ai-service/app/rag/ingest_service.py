from pathlib import Path
from sqlalchemy import select

from app.rag.document_loader import load_document
from app.rag.models import RagChunk, RagDocument


class IngestService:
    def __init__(self, session, chunker, embedder) -> None:
        self.session = session
        self.chunker = chunker
        self.embedder = embedder

    async def ingest(self, path: Path, knowledge_base_id: str) -> dict:
        document = load_document(path)
        statement = select(RagDocument).where(
            RagDocument.knowledge_base_id == knowledge_base_id,
            RagDocument.content_hash == document.content_hash,
        )
        existing = (await self.session.execute(statement)).scalars().first()
        if existing:
            return {"status": "skipped", "document_id": existing.id}

        chunks = self.chunker.splits(document)
        embeddings = await self.embedder.embed_documents(
            [chunk.content for chunk in chunks]
        )
        rag_document = RagDocument(
            knowledge_base_id=knowledge_base_id,
            file_name=document.file_name,
            content_type=document.content_type,
            content_hash=document.content_hash,
        )
        self.session.add(rag_document)
        await self.session.flush()

        for chunk, embedding in zip(chunks, embeddings, strict=True):
            self.session.add(
                RagChunk(
                    document_id=rag_document.id,
                    knowledge_base_id=knowledge_base_id,
                    chunk_index=chunk.chunk_index,
                    content=chunk.content,
                    char_start=chunk.char_start,
                    char_end=chunk.char_end,
                    token_count=len(chunk.content.split()),
                    metadata_=chunk.metadata,
                    embedding=embedding,
                )
            )

        await self.session.flush()
        return {
            "status": "imported",
            "document_id": rag_document.id,
            "chunks": len(chunks),
        }

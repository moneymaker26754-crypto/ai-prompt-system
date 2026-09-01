from pathlib import Path
import hashlib
from uuid import uuid4

import pytest

from app.rag.ingest_service import IngestService
from app.rag.models import RagChunk, RagDocument
from app.rag.chunker import RecursiveChunker


class _Scalars:
    def __init__(self, value):
        self._value = value

    def first(self):
        return self._value


class _Result:
    def __init__(self, value):
        self._value = value

    def scalars(self):
        return _Scalars(self._value)


class RecordingSession:
    def __init__(self, existing=None):
        self.existing = existing
        self.added = []
        self.deleted = []

    async def execute(self, statement):
        self.statement = statement
        return _Result(self.existing)

    def add(self, instance) -> None:
        self.added.append(instance)

    async def delete(self, instance) -> None:
        self.deleted.append(instance)

    async def flush(self) -> None:
        for instance in self.added:
            if isinstance(instance, RagDocument) and instance.id is None:
                instance.id = uuid4()


class DeduplicatingSession(RecordingSession):
    def __init__(self, documents: list[RagDocument]):
        super().__init__()
        self.documents = documents

    async def execute(self, statement):
        self.statement = statement
        values = set(statement.compile().params.values())
        existing = next(
            (
                document
                for document in self.documents
                if document.knowledge_base_id in values
                and document.content_hash in values
            ),
            None,
        )
        return _Result(existing)


class FixedEmbedder:
    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        self.texts = texts
        return [[0.25] * 1024 for _ in texts]


@pytest.mark.anyio
async def test_ingest_persists_document_and_chunks_for_the_requested_knowledge_base(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source_file = tmp_path / "knowledge" / "intro.md"
    source_file.parent.mkdir()
    source_file.write_text("abcdefghij", encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    session = RecordingSession()
    embedder = FixedEmbedder()
    knowledge_base_id = "knowledge-base-1"

    result = await IngestService(
        session=session,
        chunker=RecursiveChunker(
            chunk_size=5,
            overlap=2,
        ),
        embedder=embedder,
    ).ingest(source_file, knowledge_base_id)

    document = next(item for item in session.added if isinstance(item, RagDocument))
    chunks = [item for item in session.added if isinstance(item, RagChunk)]
    assert result == {
        "status": "imported",
        "document_id": document.id,
        "chunks": 3,
    }
    assert document.knowledge_base_id == knowledge_base_id
    assert document.file_name == "intro.md"
    assert document.content_type == "text/markdown"
    assert [chunk.knowledge_base_id for chunk in chunks] == [knowledge_base_id] * 3
    assert [chunk.chunk_index for chunk in chunks] == [0, 1, 2]
    assert [chunk.char_end for chunk in chunks] == [5, 8, 10]
    assert chunks[1].metadata_ == {
        "source": "knowledge/intro.md",
        "file_name": "intro.md",
        "chunk_index": 1,
        "char_start": 3,
        "char_end": 8,
    }
    assert embedder.texts == ["abcde", "defgh", "ghij"]
    assert chunks[0].embedding == [0.25] * 1024


@pytest.mark.anyio
async def test_ingest_skips_a_duplicate_content_hash_in_the_same_knowledge_base(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source_file = tmp_path / "knowledge" / "intro.md"
    source_file.parent.mkdir()
    source_file.write_text("abcdefghij", encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    existing = RagDocument(
        id=uuid4(),
        knowledge_base_id="knowledge-base-1",
        file_name="existing.md",
        content_type="text/markdown",
        content_hash=hashlib.sha256(b"abcdefghij").hexdigest(),
    )
    session = DeduplicatingSession([existing])
    embedder = FixedEmbedder()

    result = await IngestService(
        session=session,
        chunker=RecursiveChunker(chunk_size=5, overlap=2),
        embedder=embedder,
    ).ingest(source_file, "knowledge-base-1")

    assert result == {"status": "skipped", "document_id": existing.id}
    assert session.added == []
    assert not hasattr(embedder, "texts")


@pytest.mark.anyio
async def test_ingest_imports_the_same_content_hash_for_a_different_knowledge_base(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source_file = tmp_path / "knowledge" / "intro.md"
    source_file.parent.mkdir()
    source_file.write_text("abcdefghij", encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    existing = RagDocument(
        id=uuid4(),
        knowledge_base_id="knowledge-base-1",
        file_name="existing.md",
        content_type="text/markdown",
        content_hash=hashlib.sha256(b"abcdefghij").hexdigest(),
    )
    session = DeduplicatingSession([existing])
    embedder = FixedEmbedder()

    result = await IngestService(
        session=session,
        chunker=RecursiveChunker(chunk_size=5, overlap=2),
        embedder=embedder,
    ).ingest(source_file, "knowledge-base-2")

    document = next(item for item in session.added if isinstance(item, RagDocument))
    assert result["status"] == "imported"
    assert document.knowledge_base_id == "knowledge-base-2"

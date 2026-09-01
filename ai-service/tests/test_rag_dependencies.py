from types import SimpleNamespace

import pytest
from fastapi import FastAPI

from app.api.dependencies import get_ingest_service, get_reranker
from app.rag.chunker import RecursiveChunker
from app.rag.ingest_service import IngestService


def test_get_ingest_service_assembles_the_provided_rag_dependencies() -> None:
    session = object()
    chunker = RecursiveChunker(chunk_size=5, overlap=1)
    embedder = object()

    service = get_ingest_service(
        session=session,
        chunker=chunker,
        embedder=embedder,
    )

    assert isinstance(service, IngestService)
    assert service.session is session
    assert service.chunker is chunker
    assert service.embedder is embedder


def test_get_reranker_requires_the_lifespan_owned_instance() -> None:
    with pytest.raises(AttributeError):
        get_reranker(SimpleNamespace(app=FastAPI()))

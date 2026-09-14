from types import SimpleNamespace

from app.api.dependencies import (
    get_chunker,
    get_dense_retriever,
    get_embedding_service,
    get_evaluation_service,
    get_ingest_service,
    get_ollama_client,
    get_retrieval_service,
    get_grounded_answer_service,
)
from app.core.config import Settings
from app.rag.chunker import RecursiveChunker
from app.rag.evaluation.evaluate import EvaluationService
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


def test_get_chunker_uses_injected_settings() -> None:
    chunker = get_chunker(
        settings=Settings(
            rag_chunk_size=600,
            rag_chunk_overlap=80,
        )
    )

    assert chunker.chunk_size == 600
    assert chunker.overlap == 80


def test_get_grounded_answer_service_returns_the_lifespan_owned_instance() -> None:
    grounded_answer_service = object()
    request = SimpleNamespace(
        app=SimpleNamespace(
            state=SimpleNamespace(grounded_answer_service=grounded_answer_service)
        )
    )

    assert get_grounded_answer_service(request) is grounded_answer_service


def test_evaluation_service_uses_lifespan_owned_retrieval_dependencies() -> None:
    dense = object()
    retrieval = object()
    evaluation = get_evaluation_service(
        retrieval_service=retrieval,
        dense_retriever=dense,
    )

    assert isinstance(evaluation, EvaluationService)
    assert evaluation.retrieval_service is retrieval
    assert evaluation.dense_retriever is dense


def test_rag_singleton_dependencies_return_lifespan_owned_instances() -> None:
    ollama_client = object()
    embedding_service = object()
    dense_retriever = object()
    retrieval_service = object()
    state = SimpleNamespace(
        ollama_client=ollama_client,
        embedding_service=embedding_service,
        dense_retriever=dense_retriever,
        retrieval_service=retrieval_service,
    )
    request = SimpleNamespace(app=SimpleNamespace(state=state))

    assert get_ollama_client(request) is ollama_client
    assert get_embedding_service(request) is embedding_service
    assert get_dense_retriever(request) is dense_retriever
    assert get_retrieval_service(request) is retrieval_service

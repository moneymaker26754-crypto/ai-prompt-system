from fastapi import FastAPI
import importlib
import pytest

from app.core.lifespan import lifespan
from app.clients.ollama import OllamaClient
from app.rag.embedder import EmbeddingService
from app.rag.generation.grounded_answer_service import GroundedAnswerService
from app.rag.retrieval.dense_retriever import DenseRetriever
from app.rag.retrieval.reranker import BgeReranker
from app.rag.retrieval.retrieval_service import RetrievalService


@pytest.mark.anyio
async def test_lifespan_exposes_an_async_session_factory_on_app_state() -> None:
    app = FastAPI()

    async with lifespan(app):
        assert app.state.session_factory.kw["expire_on_commit"] is False
        assert str(app.state.db_engine.url).startswith("postgresql+asyncpg://")
        assert isinstance(app.state.rag_reranker, BgeReranker)
        assert app.state.rag_reranker._model is None
        assert isinstance(app.state.ollama_client, OllamaClient)
        assert isinstance(app.state.embedding_service, EmbeddingService)
        assert isinstance(app.state.dense_retriever, DenseRetriever)
        assert isinstance(app.state.retrieval_service, RetrievalService)
        assert isinstance(app.state.grounded_answer_service, GroundedAnswerService)
        assert app.state.embedding_service.ollama_client is app.state.ollama_client
        assert app.state.dense_retriever.embedder is app.state.embedding_service
        assert app.state.retrieval_service.reranker is app.state.rag_reranker
        assert (
            app.state.grounded_answer_service.retriever.retrieval_service
            is app.state.retrieval_service
        )
        llm = app.state.grounded_answer_service.generator.llm
        sync_transport = llm._client._client
        async_transport = llm._async_client._client

    assert sync_transport.is_closed
    assert async_transport.is_closed


@pytest.mark.anyio
async def test_lifespan_cleans_up_resources_when_startup_fails(monkeypatch) -> None:
    lifespan_module = importlib.import_module("app.core.lifespan")

    class FakeHttpClient:
        def __init__(self):
            self.closed = False

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            self.closed = True

    class FakeEngine:
        def __init__(self):
            self.disposed = False

        async def dispose(self):
            self.disposed = True

    http_client = FakeHttpClient()
    engine = FakeEngine()
    monkeypatch.setattr(lifespan_module.httpx, "AsyncClient", lambda **kwargs: http_client)
    monkeypatch.setattr(lifespan_module, "create_async_engine", lambda url: engine)
    monkeypatch.setattr(
        lifespan_module,
        "create_session_factory",
        lambda created_engine: object(),
    )
    monkeypatch.setattr(
        lifespan_module,
        "build_grounded_answer_service",
        lambda **kwargs: (_ for _ in ()).throw(RuntimeError("startup failed")),
    )

    with pytest.raises(RuntimeError, match="startup failed"):
        async with lifespan(FastAPI()):
            pass

    assert http_client.closed
    assert engine.disposed

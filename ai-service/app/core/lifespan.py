from contextlib import AsyncExitStack, asynccontextmanager

import httpx
from fastapi import FastAPI

from app.clients.ollama import OllamaClient
from app.core.config import get_settings
from app.database import create_async_engine, create_session_factory
from app.rag.embedder import EmbeddingService
from app.rag.generation.factory import build_grounded_answer_service
from app.rag.retrieval.dense_retriever import DenseRetriever
from app.rag.retrieval.hybrid_retriever import HybridRetriever
from app.rag.retrieval.keyword_retriever import KeywordRetriever
from app.rag.retrieval.reranker import BgeReranker
from app.rag.retrieval.retrieval_service import RetrievalService


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    timeout = httpx.Timeout(
        timeout=settings.ollama_read_timeout,
        connect=settings.ollama_connect_timeout,
    )

    async with AsyncExitStack() as stack:
        app.state.http_client = await stack.enter_async_context(
            httpx.AsyncClient(
                base_url=settings.ollama_base_url,
                timeout=timeout,
            )
        )
        app.state.db_engine = create_async_engine(settings.rag_database_url)
        stack.push_async_callback(app.state.db_engine.dispose)

        app.state.session_factory = create_session_factory(app.state.db_engine)
        app.state.rag_reranker = BgeReranker(settings.rag_reranker_model)
        app.state.ollama_client = OllamaClient(
            http_client=app.state.http_client,
            settings=settings,
        )
        app.state.embedding_service = EmbeddingService(
            ollama_client=app.state.ollama_client,
            model=settings.rag_embedding_model,
        )
        app.state.dense_retriever = DenseRetriever(
            app.state.embedding_service,
            app.state.session_factory,
        )
        keyword_retriever = KeywordRetriever(app.state.session_factory)
        hybrid_retriever = HybridRetriever(
            app.state.dense_retriever,
            keyword_retriever,
        )
        app.state.retrieval_service = RetrievalService(
            hybrid_retriever=hybrid_retriever,
            reranker=app.state.rag_reranker,
        )
        app.state.grounded_answer_service = build_grounded_answer_service(
            retrieval_service=app.state.retrieval_service,
            settings=settings,
            exit_stack=stack,
        )

        yield

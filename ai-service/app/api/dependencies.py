import secrets

from fastapi import Depends, HTTPException, Request

from app.clients.ollama import OllamaClient
from app.core.config import Settings, get_settings
from app.database import get_session
from app.rag.chunker import RecursiveChunker
from app.rag.embedder import EmbeddingService
from app.rag.evaluation.evaluate import EvaluationService
from app.rag.generation.grounded_answer_service import GroundedAnswerService
from app.rag.ingest_service import IngestService
from app.rag.retrieval.dense_retriever import DenseRetriever
from app.rag.retrieval.retrieval_service import RetrievalService
from app.services.analyze import PromptAnalyzeService
from app.services.optimize import PromptOptimizeService
from app.services.optimize_stream import PromptOptimizeStreamService
from app.services.review import PromptReviewService


def get_ollama_client(request: Request) -> OllamaClient:
    return request.app.state.ollama_client


def get_embedding_service(request: Request) -> EmbeddingService:
    return request.app.state.embedding_service


def get_dense_retriever(request: Request) -> DenseRetriever:
    return request.app.state.dense_retriever


def get_retrieval_service(request: Request) -> RetrievalService:
    return request.app.state.retrieval_service


def get_grounded_answer_service(request: Request) -> GroundedAnswerService:
    return request.app.state.grounded_answer_service


def get_prompt_analyze_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptAnalyzeService:
    return PromptAnalyzeService(ollama_client=ollama_client)


def get_prompt_optimize_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptOptimizeService:
    return PromptOptimizeService(ollama_client=ollama_client)


def get_prompt_review_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptReviewService:
    return PromptReviewService(ollama_client=ollama_client)


def get_prompt_optimize_stream_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptOptimizeStreamService:
    return PromptOptimizeStreamService(ollama_client=ollama_client)


def get_chunker(
    settings: Settings = Depends(get_settings),
) -> RecursiveChunker:
    return RecursiveChunker(
        chunk_size=settings.rag_chunk_size,
        overlap=settings.rag_chunk_overlap,
    )


def get_ingest_service(
    session=Depends(get_session),
    chunker: RecursiveChunker = Depends(get_chunker),
    embedder: EmbeddingService = Depends(get_embedding_service),
) -> IngestService:
    return IngestService(
        session=session,
        chunker=chunker,
        embedder=embedder,
    )


def get_evaluation_service(
    retrieval_service: RetrievalService = Depends(get_retrieval_service),
    dense_retriever: DenseRetriever = Depends(get_dense_retriever),
) -> EvaluationService:
    return EvaluationService(retrieval_service, dense_retriever)


def require_internal_api_key(
    request: Request,
    settings: Settings = Depends(get_settings),
) -> None:
    provided_key = request.headers.get("X-Internal-API-Key")
    expected_key = settings.internal_api_key
    if not expected_key or not provided_key or not secrets.compare_digest(
        provided_key,
        expected_key,
    ):
        raise HTTPException(status_code=401, detail="Unauthorized")

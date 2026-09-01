import secrets

from fastapi import Depends, HTTPException, Request

from app.clients.ollama import OllamaClient
from app.core.config import  get_settings
from app.database import get_session
from app.rag.chunker import RecursiveChunker
from app.rag.embedder import EmbeddingService
from app.rag.evaluate import EvaluationService
from app.rag.ingest_service import IngestService
from app.rag.reranker import BgeReranker
from app.rag.retrieval_service import RetrievalService
from app.rag.vector_store import PgVectorStore
from app.services.analyze import PromptAnalyzeService
from app.services.optimize import PromptOptimizeService
from app.services.optimize_stream import PromptOptimizeStreamService
from app.services.review import PromptReviewService

# lifespan代替Depend，实现多次调用都用一个实例
# async def get_ollama_client(
#     settings: Settings=Depends(get_settings)
# ) -> AsyncIterator[OllamaClient]:
#     timeout = httpx.Timeout(
#         timeout=settings.ollama_read_timeout,
#         connect=settings.ollama_connect_timeout,
#     )
#
#     async with httpx.AsyncClient(
#         base_url=settings.ollama_base_url,
#         timeout=timeout,
#     ) as http_client:
#         yield OllamaClient(
#             http_client=http_client,
#             settings=settings,
#         )


def get_ollama_client(request: Request,) -> OllamaClient:
    return OllamaClient(http_client=request.app.state.http_client, settings=get_settings(),)


def get_prompt_analyze_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptAnalyzeService:
    return PromptAnalyzeService(
        ollama_client=ollama_client,
    )


def get_prompt_optimize_service(
        ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptOptimizeService:
    return PromptOptimizeService(
        ollama_client=ollama_client
    )


def get_prompt_review_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptReviewService:
    return PromptReviewService(
        ollama_client=ollama_client
    )


def get_prompt_optimize_stream_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptOptimizeStreamService:
    return PromptOptimizeStreamService(
        ollama_client=ollama_client
    )

def get_chunker() -> RecursiveChunker:
    settings = get_settings()
    return RecursiveChunker(
        chunk_size=settings.rag_chunk_size,
        overlap=settings.rag_chunk_overlap,
    )


def get_embedding_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> EmbeddingService:
    return EmbeddingService(
        ollama_client=ollama_client,
        model=get_settings().rag_embedding_model,
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


def get_vector_store(session=Depends(get_session)) -> PgVectorStore:
    return PgVectorStore(session)


def get_reranker(request: Request) -> BgeReranker:
    return request.app.state.rag_reranker


def get_retrieval_service(
    embedder: EmbeddingService = Depends(get_embedding_service),
    vector_store: PgVectorStore = Depends(get_vector_store),
    reranker: BgeReranker = Depends(get_reranker),
) -> RetrievalService:
    return RetrievalService(
        embedder=embedder,
        vector_store=vector_store,
        reranker=reranker,
    )


def get_evaluation_service(
    retrieval_service: RetrievalService = Depends(get_retrieval_service),
) -> EvaluationService:
    return EvaluationService(retrieval_service)


def require_internal_api_key(
    request: Request,
    settings=Depends(get_settings),
) -> None:
    provided_key = request.headers.get("X-Internal-API-Key")
    expected_key = settings.internal_api_key
    if not expected_key or not provided_key or not secrets.compare_digest(
        provided_key, expected_key
    ):
        raise HTTPException(status_code=401, detail="Unauthorized")

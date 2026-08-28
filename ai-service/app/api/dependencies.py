from fastapi import Request

from fastapi import Depends

from app.clients.ollama import OllamaClient
from app.core.config import  get_settings
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

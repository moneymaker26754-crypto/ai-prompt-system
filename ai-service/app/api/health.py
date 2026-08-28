from fastapi import APIRouter, Depends

from app.api.dependencies import get_ollama_client
from app.clients.ollama import OllamaClient
from app.core.config import get_settings
from app.schemas.health import HealthResponse


router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    settings = get_settings()

    return HealthResponse(
        service=settings.service_name,
        version=settings.version
    )


@router.get("/health/ready")
async def readiness(ollama_client: OllamaClient = Depends(get_ollama_client),):
    await ollama_client.check_ready()

    return {
        "status": "ready",
        "service": "ai-prompt-service",
    }
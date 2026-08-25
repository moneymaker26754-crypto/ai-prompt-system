from collections.abc import AsyncIterator

import httpx
from fastapi import Depends

from app.clients.ollama import OllamaClient
from app.core.config import Settings, get_settings
from app.services.analyze import PromptAnalyzeService


async def get_ollama_client(
    settings: Settings=Depends(get_settings)
) -> AsyncIterator[OllamaClient]:
    timeout = httpx.Timeout(
        timeout=settings.ollama_read_timeout,
        connect=settings.ollama_connect_timeout,
    )

    async with httpx.AsyncClient(
        base_url=settings.ollama_base_url,
        timeout=timeout,
    ) as http_client:
        yield OllamaClient(
            http_client=http_client,
            settings=settings,
        )


def get_prompt_analyze_service(
    ollama_client: OllamaClient = Depends(get_ollama_client),
) -> PromptAnalyzeService:
    return PromptAnalyzeService(
        ollama_client=ollama_client,
    )

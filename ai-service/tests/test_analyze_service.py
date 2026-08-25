from unittest.mock import AsyncMock

import pytest

from app.clients.ollama import OllamaClient
from app.core.config import Settings
from app.schemas.analyze import AnalyzeRequest
from app.schemas.ollama import OllamaGenerateResponse
from app.services.analyze import PromptAnalyzeService


@pytest.mark.anyio
async def test_analyze_returns_analysis_result():
    ollama_client = AsyncMock(spec=OllamaClient)

    ollama_client.generate.return_value = OllamaGenerateResponse(
        model="qwen3.5:9b",
        response="analysis result",
        done=True,
    )

    service = PromptAnalyzeService(
        ollama_client=ollama_client,
    )

    result = await service.analyze(
        AnalyzeRequest(
            original_prompt="Please help me to write something"
        )
    )

    assert result.analysis == "analysis result"
    assert result.model == "qwen3.5:9b"


@pytest.mark.anyio
async def test_analyze_rejects_empty_model_response():
    ollama_client = AsyncMock(spec=OllamaClient)

    ollama_client.generate.return_value = OllamaGenerateResponse(
        model="qwen3.5:9b",
        response="   ",
        done=True,
    )

    service = PromptAnalyzeService(
        ollama_client=ollama_client,
    )

    from app.core.exceptions import InvalidModelOutputError

    with pytest.raises(InvalidModelOutputError):
        await service.analyze(
            AnalyzeRequest(
                original_prompt="hello"
            )
        )




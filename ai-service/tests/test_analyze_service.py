from unittest.mock import AsyncMock

import pytest
from prometheus_client import REGISTRY

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


@pytest.mark.anyio
async def test_analyze_records_success_duration_and_token_metrics():
    ollama_client = AsyncMock(spec=OllamaClient)
    ollama_client.generate.return_value = OllamaGenerateResponse(
        model="qwen3.5:9b",
        response="analysis result",
        done=True,
        prompt_eval_count=11,
        eval_count=7,
    )
    service = PromptAnalyzeService(ollama_client=ollama_client)
    labels = {"operation": "analyze"}
    status_labels = {**labels, "status": "success"}
    request_before = REGISTRY.get_sample_value("ai_request_total", status_labels) or 0
    duration_before = REGISTRY.get_sample_value("ai_request_duration_seconds_count", labels) or 0
    input_before = REGISTRY.get_sample_value("ai_input_tokens_total", labels) or 0
    output_before = REGISTRY.get_sample_value("ai_output_tokens_total", labels) or 0

    await service.analyze(AnalyzeRequest(original_prompt="hello"))

    assert REGISTRY.get_sample_value("ai_request_total", status_labels) - request_before == 1
    assert REGISTRY.get_sample_value("ai_request_duration_seconds_count", labels) - duration_before == 1
    assert REGISTRY.get_sample_value("ai_input_tokens_total", labels) - input_before == 11
    assert REGISTRY.get_sample_value("ai_output_tokens_total", labels) - output_before == 7


@pytest.mark.anyio
async def test_analyze_records_timeout_metric():
    from app.core.exceptions import ModelTimeoutError

    ollama_client = AsyncMock(spec=OllamaClient)
    ollama_client.generate.side_effect = ModelTimeoutError("timed out")
    service = PromptAnalyzeService(ollama_client=ollama_client)
    labels = {"operation": "analyze", "status": "timeout"}
    before = REGISTRY.get_sample_value("ai_request_total", labels) or 0

    with pytest.raises(ModelTimeoutError):
        await service.analyze(AnalyzeRequest(original_prompt="hello"))

    assert REGISTRY.get_sample_value("ai_request_total", labels) - before == 1




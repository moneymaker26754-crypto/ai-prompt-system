from unittest.mock import AsyncMock

import pytest
from prometheus_client import REGISTRY

from app.clients.ollama import OllamaClient
from app.schemas.ollama import OllamaGenerateResponse
from app.schemas.optimize import OptimizeRequest
from app.services.optimize import PromptOptimizeService

# 注释为标记该测试为异步测试，支持 async/await
@pytest.mark.anyio
async def test_optimize_returns_model_result():
    ollama_client = AsyncMock(
        spec=OllamaClient
    )

    ollama_client.generate.return_value = (
        OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="优化后的提示词",
            done=True,
        )
    )

    service = PromptOptimizeService(
        ollama_client=ollama_client
    )

    result = await service.optimize(
        OptimizeRequest(
            original_prompt="帮我写文章",
            analysis_result="缺少输出约束",
            target="更明确",
        )
    )

    assert result.optimized_prompt == "优化后的提示词"
    assert result.model == "qwen3.5:9b"


@pytest.mark.anyio
async def test_optimize_rejects_empty_output():
    ollama_client = AsyncMock(
        spec=OllamaClient
    )

    ollama_client.generate.return_value = (
        OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="   ",
            done=True,
        )
    )

    service = PromptOptimizeService(
        ollama_client=ollama_client
    )

    from app.core.exceptions import (
        InvalidModelOutputError,
    )

    with pytest.raises(
            InvalidModelOutputError
    ):
        await service.optimize(
            OptimizeRequest(
                original_prompt="hello",
                analysis_result="analysis",
            )
        )


@pytest.mark.anyio
async def test_optimize_records_success_and_token_metrics():
    ollama_client = AsyncMock(spec=OllamaClient)
    ollama_client.generate.return_value = OllamaGenerateResponse(
        model="qwen3.5:9b",
        response="optimized",
        done=True,
        prompt_eval_count=13,
        eval_count=5,
    )
    service = PromptOptimizeService(ollama_client=ollama_client)
    status_labels = {"operation": "optimize", "status": "success"}
    operation_labels = {"operation": "optimize"}
    request_before = REGISTRY.get_sample_value("ai_request_total", status_labels) or 0
    input_before = REGISTRY.get_sample_value("ai_input_tokens_total", operation_labels) or 0
    output_before = REGISTRY.get_sample_value("ai_output_tokens_total", operation_labels) or 0

    await service.optimize(
        OptimizeRequest(original_prompt="hello", analysis_result="analysis")
    )

    assert REGISTRY.get_sample_value("ai_request_total", status_labels) - request_before == 1
    assert REGISTRY.get_sample_value("ai_input_tokens_total", operation_labels) - input_before == 13
    assert REGISTRY.get_sample_value("ai_output_tokens_total", operation_labels) - output_before == 5

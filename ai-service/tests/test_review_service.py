from unittest.mock import AsyncMock

import pytest
from prometheus_client import REGISTRY

from app.clients.ollama import OllamaClient
from app.schemas.ollama import OllamaGenerateResponse
from app.schemas.review import ReviewRequest
from app.services.review import PromptReviewService


@pytest.mark.anyio
async def test_review_returns_valid_structured_result():
    ollama_client = AsyncMock(
        spec=OllamaClient
    )

    ollama_client.generate.return_value = (
        OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="""
            {
              "score": 88,
              "risk_level": "LOW",
              "changed_intent": false,
              "review_comment": "优化结果保持了原意"
            }
            """,
            done=True,
        )
    )

    service = PromptReviewService(
        ollama_client=ollama_client
    )

    result = await service.review(
        ReviewRequest(
            original_prompt="hello",
            optimized_prompt="better hello",
        )
    )

    assert result.score == 88
    assert result.risk_level.value == "LOW"
    assert result.changed_intent is False


@pytest.mark.anyio
async def test_review_records_invalid_output_metric_and_tokens():
    from app.core.exceptions import InvalidModelOutputError

    ollama_client = AsyncMock(spec=OllamaClient)
    ollama_client.generate.return_value = OllamaGenerateResponse(
        model="qwen3.5:9b",
        response="not json at all",
        done=True,
        prompt_eval_count=19,
        eval_count=4,
    )
    service = PromptReviewService(ollama_client=ollama_client)
    status_labels = {"operation": "review", "status": "invalid_output"}
    operation_labels = {"operation": "review"}
    request_before = REGISTRY.get_sample_value("ai_request_total", status_labels) or 0
    input_before = REGISTRY.get_sample_value("ai_input_tokens_total", operation_labels) or 0
    output_before = REGISTRY.get_sample_value("ai_output_tokens_total", operation_labels) or 0

    with pytest.raises(InvalidModelOutputError):
        await service.review(
            ReviewRequest(original_prompt="hello", optimized_prompt="better hello")
        )

    assert REGISTRY.get_sample_value("ai_request_total", status_labels) - request_before == 1
    assert REGISTRY.get_sample_value("ai_input_tokens_total", operation_labels) - input_before == 19
    assert REGISTRY.get_sample_value("ai_output_tokens_total", operation_labels) - output_before == 4

from unittest.mock import AsyncMock

import pytest

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
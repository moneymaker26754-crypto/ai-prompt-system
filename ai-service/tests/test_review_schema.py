from unittest.mock import AsyncMock

import pytest
from pydantic import ValidationError

from app.clients.ollama import OllamaClient
from app.schemas.ollama import OllamaGenerateResponse
from app.schemas.review import (ReviewRequest, ReviewResponse, RiskLevel)
from app.services.review import PromptReviewService


def test_review_request_accepts_valid_data():
    request = ReviewRequest(
        original_prompt="帮我写产品介绍",
        optimized_prompt="请为产品生成三段式专业介绍",
    )

    assert request.original_prompt == "帮我写产品介绍"
    assert request.optimized_prompt == "请为产品生成三段式专业介绍"


def test_review_request_rejects_blank_original_prompt():
    with pytest.raises(ValidationError):
        ReviewRequest(
            original_prompt="   ",
            optimized_prompt="valid",
        )


def test_review_request_rejects_blank_optimized_prompt():
    with pytest.raises(ValidationError):
        ReviewRequest(
            original_prompt="valid",
            optimized_prompt="   ",
        )



def test_review_response_accepts_valid_result():
    response = ReviewResponse(
        score=85,
        risk_level=RiskLevel.LOW,
        changed_intent=False,
        review_comment="未改变原意",
        model="qwen3.5:9b",
    )

    assert response.score == 85
    assert response.risk_level == RiskLevel.LOW


def test_review_response_rejects_score_over_100():
    with pytest.raises(ValidationError):
        ReviewResponse(
            score=101,
            risk_level=RiskLevel.LOW,
            changed_intent=False,
            review_comment="test",
            model="qwen3.5:9b",
        )


def test_review_response_rejects_invalid_risk_level():
    with pytest.raises(ValidationError):
        ReviewResponse(
            score=80,
            risk_level="UNKNOWN",
            changed_intent=False,
            review_comment="test",
            model="qwen3.5:9b",
        )


@pytest.mark.anyio
async def test_review_rejects_invalid_score():
    ollama_client = AsyncMock(
        spec=OllamaClient
    )

    ollama_client.generate.return_value = (
        OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="""
            {
              "score": 120,
              "risk_level": "LOW",
              "changed_intent": false,
              "review_comment": "ok"
            }
            """,
            done=True,
        )
    )

    service = PromptReviewService(
        ollama_client=ollama_client
    )

    from app.core.exceptions import (
        InvalidModelOutputError,
    )

    with pytest.raises(
            InvalidModelOutputError
    ):
        await service.review(
            ReviewRequest(
                original_prompt="a",
                optimized_prompt="b",
            )
        )
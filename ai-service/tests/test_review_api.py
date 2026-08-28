from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_prompt_review_service,
)
from app.main import app
from app.schemas.review import (
    ReviewResponse,
    RiskLevel,
)


def test_review_api_returns_structured_result():
    fake_service = AsyncMock()

    fake_service.review.return_value = (
        ReviewResponse(
            score=92,
            risk_level=RiskLevel.LOW,
            changed_intent=False,
            review_comment="保持原意",
            model="qwen3.5:9b",
        )
    )

    app.dependency_overrides[
        get_prompt_review_service
    ] = lambda: fake_service

    try:
        with TestClient(app) as client:
            response = client.post(
                "/v1/prompts/review",
                json={
                    "original_prompt": "hello",
                    "optimized_prompt": "better hello",
                },
            )

        assert response.status_code == 200

        assert response.json() == {
            "score": 92,
            "risk_level": "LOW",
            "changed_intent": False,
            "review_comment": "保持原意",
            "model": "qwen3.5:9b",
        }

    finally:
        app.dependency_overrides.clear()
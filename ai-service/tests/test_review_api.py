import logging
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
from app.core.exceptions import InvalidModelOutputError


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


def test_review_invalid_model_output_uses_distinct_error_type():
    fake_service = AsyncMock()
    fake_service.review.side_effect = InvalidModelOutputError("invalid output")
    records = []

    class RecordHandler(logging.Handler):
        def emit(self, record):
            records.append(record)

    logger = logging.getLogger("app.core.error_handlers")
    handler = RecordHandler()
    logger.addHandler(handler)
    app.dependency_overrides[get_prompt_review_service] = lambda: fake_service

    try:
        with TestClient(app) as client:
            response = client.post(
                "/v1/prompts/review",
                headers={"X-Request-ID": "observability-test-1"},
                json={"original_prompt": "hello", "optimized_prompt": "better"},
            )
    finally:
        logger.removeHandler(handler)
        app.dependency_overrides.clear()

    assert response.status_code == 502
    assert response.json()["code"] == "INVALID_MODEL_OUTPUT"
    assert response.headers["X-Request-ID"] == "observability-test-1"
    assert records[-1].errorType == "invalid_output"

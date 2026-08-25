from unittest.mock import AsyncMock

import pytest
from pydantic import ValidationError
from starlette.testclient import TestClient

from app.api.dependencies import get_prompt_analyze_service
from app.core.exceptions import ModelUnavailableError
from app.main import app
from app.schemas.analyze import AnalyzeRequest


def test_analyze_request_accept_valid_prompt():
    request = AnalyzeRequest(
        original_prompt="Please help me to write a description for a product"
    )

    assert request.original_prompt =="Please help me to write a description for a product"


def test_analyze_request_rejects_blank_prompt():
    with pytest.raises(ValidationError):
        AnalyzeRequest(original_prompt="   ")


def test_analyze_request_rejects_longer_than_5000():
    with pytest.raises(ValidationError):
        AnalyzeRequest(original_prompt="a" * 5001)


def test_analyze_returns_503_when_model_unavailable():
    fake_service = AsyncMock()

    fake_service.analyze.side_effect = ModelUnavailableError(
        "Unable to connect to Ollama"
    )

    app.dependency_overrides[
        get_prompt_analyze_service
    ] = lambda: fake_service

    try:
        client = TestClient(app)

        response = client.post(
            "/v1/prompts/analyze",
            json={
                "original_prompt": "hello"
            },
        )

        assert response.status_code == 503

        assert response.json() == {
            "code": "AI_UNAVAILABLE",
            "message": "Unable to connect to Ollama",
        }

    finally:
        app.dependency_overrides.clear()
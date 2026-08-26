from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_prompt_optimize_service,
)
from app.main import app
from app.schemas.optimize import OptimizeResponse


def test_optimize_api_returns_result():
    fake_service = AsyncMock()

    fake_service.optimize.return_value = (
        OptimizeResponse(
            optimized_prompt="优化结果",
            model="qwen3.5:9b",
        )
    )

    app.dependency_overrides[
        get_prompt_optimize_service
    ] = lambda: fake_service

    try:
        with TestClient(app) as client:
            response = client.post(
                "/v1/prompts/optimize",
                json={
                    "original_prompt": "hello",
                    "analysis_result": "analysis",
                    "target": "more clear",
                },
            )

        assert response.status_code == 200

        assert response.json() == {
            "optimized_prompt": "优化结果",
            "model": "qwen3.5:9b",
        }

    finally:
        app.dependency_overrides.clear()
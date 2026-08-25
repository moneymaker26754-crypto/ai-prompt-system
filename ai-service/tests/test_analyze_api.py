from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_prompt_analyze_service,
)
from app.main import app
from app.schemas.analyze import AnalyzeResponse


def test_analyze_api_returns_result():
    fake_service = AsyncMock()

    fake_service.analyze.return_value = AnalyzeResponse(
        analysis="测试分析结果",
        model="qwen3.5:9b",
    )

    app.dependency_overrides[
        get_prompt_analyze_service
    ] = lambda: fake_service

    try:
        client = TestClient(app)

        response = client.post(
            "/v1/prompts/analyze",
            json={
                "original_prompt": "帮我写文章"
            },
        )

        assert response.status_code == 200
        assert response.json() == {
            "analysis": "测试分析结果",
            "model": "qwen3.5:9b",
        }

    finally:
        app.dependency_overrides.clear()
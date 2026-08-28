from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from app.api.dependencies import get_ollama_client
from app.main import app


Client = TestClient(app)


def test_health_return_ok():
    response = Client.get("/v1/health")


    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "ai-prompt-service",
        "version": "0.1.0",
    }


def test_readiness_return_200():
    fake_client = AsyncMock()
    fake_client.check_ready.return_value = None

    app.dependency_overrides[get_ollama_client] = lambda: fake_client

    try:
        with TestClient(app) as client:
            response = client.get("/v1/health/ready")
            assert response.status_code == 200
    finally:
        app.dependency_overrides.clear()

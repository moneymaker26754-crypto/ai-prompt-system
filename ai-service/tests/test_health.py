from fastapi.testclient import TestClient

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

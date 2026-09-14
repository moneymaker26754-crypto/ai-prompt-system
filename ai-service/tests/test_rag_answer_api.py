from fastapi.testclient import TestClient

from app.api.dependencies import get_grounded_answer_service
from app.core.config import Settings, get_settings
from app.main import app
from app.rag.generation.schemas import RagAnswerResponse


class FakeGroundedAnswerService:
    def __init__(self):
        self.kwargs = None

    async def answer(self, **kwargs):
        self.kwargs = kwargs
        return RagAnswerResponse(
            answerable=False,
            answer="",
            citations=[],
            reason="NO_RETRIEVAL_RESULTS",
        )


def test_answer_uses_lifespan_dependency_and_request_knowledge_base() -> None:
    service = FakeGroundedAnswerService()
    app.dependency_overrides[get_grounded_answer_service] = lambda: service
    app.dependency_overrides[get_settings] = lambda: Settings(
        internal_api_key="expected-secret"
    )

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/rag/answer",
                json={
                    "knowledge_base_id": "kb-1",
                    "question": "How do I start?",
                },
                headers={"X-Internal-API-Key": "expected-secret"},
            )

        assert response.status_code == 200
        assert response.json()["reason"] == "NO_RETRIEVAL_RESULTS"
        assert service.kwargs == {
            "question": "How do I start?",
            "knowledge_base_id": "kb-1",
        }
    finally:
        app.dependency_overrides.clear()

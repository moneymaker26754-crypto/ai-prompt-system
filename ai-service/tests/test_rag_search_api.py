from uuid import uuid4

from fastapi.testclient import TestClient

from app.api.dependencies import get_retrieval_service
from app.core.config import Settings, get_settings
from app.main import app
from app.rag.vector_store import SearchResult


class FakeRetrievalService:
    def __init__(self):
        self.result = SearchResult(
            chunk_id=uuid4(),
            document_id=uuid4(),
            content="Use the quickstart guide.",
            source="docs/quickstart.md",
            file_name="quickstart.md",
            chunk_index=2,
            char_start=30,
            char_end=55,
            vector_score=0.85,
            rerank_score=0.93,
        )

    async def retrieve(self, **kwargs):
        self.kwargs = kwargs
        return [self.result]


def test_search_requires_internal_key_and_maps_search_result_fields():
    service = FakeRetrievalService()
    app.dependency_overrides[get_retrieval_service] = lambda: service
    app.dependency_overrides[get_settings] = lambda: Settings(
        internal_api_key="expected-secret"
    )
    payload = {
        "knowledge_base_id": "kb-1",
        "query": "how do I start?",
        "top_k": 4,
        "rerank": True,
    }

    try:
        with TestClient(app) as client:
            missing = client.post("/internal/rag/search", json=payload)
            wrong = client.post(
                "/internal/rag/search",
                json=payload,
                headers={"X-Internal-API-Key": "wrong-secret"},
            )
            authorized = client.post(
                "/internal/rag/search",
                json=payload,
                headers={"X-Internal-API-Key": "expected-secret"},
            )

        assert missing.status_code == 401
        assert wrong.status_code == 401
        assert "expected-secret" not in missing.text
        assert "expected-secret" not in wrong.text
        assert service.kwargs == {
            "query": "how do I start?",
            "knowledge_base_id": "kb-1",
            "final_top_k": 4,
            "rerank": True,
        }
        assert authorized.status_code == 200
        body = authorized.json()
        assert body["query"] == "how do I start?"
        assert body["results"] == [
            {
                "rank": 1,
                "chunk_id": str(service.result.chunk_id),
                "document_id": str(service.result.document_id),
                "source": "docs/quickstart.md",
                "file_name": "quickstart.md",
                "chunk_index": 2,
                "char_start": 30,
                "char_end": 55,
                "vector_score": 0.85,
                "rerank_score": 0.93,
                "content": "Use the quickstart guide.",
            }
        ]
    finally:
        app.dependency_overrides.clear()

from fastapi.testclient import TestClient

from app.api.dependencies import get_evaluation_service
from app.core.config import Settings, get_settings
from app.main import app


class FakeEvaluationService:
    def __init__(self) -> None:
        self.calls = []

    async def evaluate(self, **kwargs):
        self.calls.append(kwargs)
        return {
            "queries": len(kwargs["cases"]),
            "recall@5": 0.8667,
            "mrr@5": 0.7412,
            "rerank": kwargs["rerank"],
        }


def test_evaluate_requires_internal_key_and_reads_configured_dataset(tmp_path):
    dataset = tmp_path / "rag_eval.jsonl"
    dataset.write_text(
        '{"knowledge_base_id":"kb-1","query":"redis","relevant_chunks":'
        '[{"source":"docs/redis.md","chunk_index":7}]}\n',
        encoding="utf-8",
    )
    service = FakeEvaluationService()
    settings = Settings(
        internal_api_key="expected-secret",
        rag_eval_dataset_dir=tmp_path,
    )
    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[get_evaluation_service] = lambda: service
    payload = {
        "dataset": "rag_eval.jsonl",
        "top_k": 5,
        "rerank": True,
        "include_failed_queries": False,
    }

    try:
        with TestClient(app) as client:
            missing = client.post("/internal/rag/evaluate", json=payload)
            wrong = client.post(
                "/internal/rag/evaluate",
                json=payload,
                headers={"X-Internal-API-Key": "wrong-secret"},
            )
            authorized = client.post(
                "/internal/rag/evaluate",
                json=payload,
                headers={"X-Internal-API-Key": "expected-secret"},
            )

        assert missing.status_code == 401
        assert wrong.status_code == 401
        assert authorized.status_code == 200
        assert authorized.json() == {
            "queries": 1,
            "recall@5": 0.8667,
            "mrr@5": 0.7412,
            "rerank": True,
        }
        assert service.calls[0]["top_k"] == 5
        assert service.calls[0]["rerank"] is True
        assert service.calls[0]["include_failed_queries"] is False
        assert service.calls[0]["cases"][0].query == "redis"
    finally:
        app.dependency_overrides.clear()


def test_evaluate_rejects_unsafe_missing_and_invalid_datasets(tmp_path):
    invalid = tmp_path / "invalid.jsonl"
    invalid.write_text("not json", encoding="utf-8")
    settings = Settings(
        internal_api_key="expected-secret",
        rag_eval_dataset_dir=tmp_path,
    )
    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[get_evaluation_service] = FakeEvaluationService
    headers = {"X-Internal-API-Key": "expected-secret"}

    try:
        with TestClient(app) as client:
            traversal = client.post(
                "/internal/rag/evaluate",
                json={"dataset": "../secret.jsonl"},
                headers=headers,
            )
            wrong_extension = client.post(
                "/internal/rag/evaluate",
                json={"dataset": "dataset.txt"},
                headers=headers,
            )
            missing = client.post(
                "/internal/rag/evaluate",
                json={"dataset": "missing.jsonl"},
                headers=headers,
            )
            malformed = client.post(
                "/internal/rag/evaluate",
                json={"dataset": "invalid.jsonl"},
                headers=headers,
            )

        assert traversal.status_code == 400
        assert wrong_extension.status_code == 400
        assert missing.status_code == 404
        assert malformed.status_code == 422
    finally:
        app.dependency_overrides.clear()


class FakeComparisonService:
    async def compare_exact_and_hnsw(self, cases):
        self.cases = cases
        return {
            "queries": len(cases),
            "exact": {
                "recall_at_5": 0.9,
                "recall_at_10": 0.91,
                "mrr_at_5": 0.77,
                "mrr_at_10": 0.78,
                "p95_latency_ms": 65.0,
            },
            "hnsw": {
                "recall_at_5": 0.88,
                "recall_at_10": 0.89,
                "mrr_at_5": 0.76,
                "mrr_at_10": 0.77,
                "p95_latency_ms": 12.0,
            },
        }


def test_compare_endpoint_loads_dataset_and_returns_both_modes(tmp_path):
    dataset = tmp_path / "rag_eval.jsonl"
    dataset.write_text(
        '{"knowledge_base_id":"kb-1","query":"redis","relevant_chunks":'
        '[{"source":"docs/redis.md","chunk_index":7}]}\n',
        encoding="utf-8",
    )
    service = FakeComparisonService()
    settings = Settings(internal_api_key="secret", rag_eval_dataset_dir=tmp_path)
    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[get_evaluation_service] = lambda: service

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/rag/evaluate/compare",
                json={"dataset": "rag_eval.jsonl"},
                headers={"X-Internal-API-Key": "secret"},
            )

        assert response.status_code == 200
        assert response.json()["exact"]["recall_at_10"] == 0.91
        assert response.json()["hnsw"]["p95_latency_ms"] == 12.0
        assert service.cases[0].query == "redis"
    finally:
        app.dependency_overrides.clear()


def test_compare_endpoint_returns_conflict_when_hnsw_index_is_missing(tmp_path):
    from app.rag.evaluate import HnswIndexNotFoundError

    class MissingIndexService:
        async def compare_exact_and_hnsw(self, cases):
            raise HnswIndexNotFoundError

    dataset = tmp_path / "rag_eval.jsonl"
    dataset.write_text(
        '{"knowledge_base_id":"kb-1","query":"redis","relevant_chunks":'
        '[{"source":"docs/redis.md","chunk_index":7}]}\n',
        encoding="utf-8",
    )
    settings = Settings(internal_api_key="secret", rag_eval_dataset_dir=tmp_path)
    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[get_evaluation_service] = MissingIndexService

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/rag/evaluate/compare",
                json={"dataset": "rag_eval.jsonl"},
                headers={"X-Internal-API-Key": "secret"},
            )

        assert response.status_code == 409
    finally:
        app.dependency_overrides.clear()

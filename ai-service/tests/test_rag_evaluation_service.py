import json
from pathlib import Path
from types import SimpleNamespace
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.rag.vector_store import SearchResult


def _result(source: str, chunk_index: int) -> SearchResult:
    return SearchResult(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content="text",
        source=source,
        file_name=Path(source).name,
        chunk_index=chunk_index,
        char_start=0,
        char_end=4,
        vector_score=0.7,
    )


def test_evaluation_case_strips_text_and_rejects_invalid_relevant_chunk():
    from app.rag.evaluate import EvaluationCase

    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "  kb-1  ",
            "query": "  where to start?  ",
            "relevant_chunks": [{"source": "docs/intro.md", "chunk_index": 0}],
        }
    )

    assert case.knowledge_base_id == "kb-1"
    assert case.query == "where to start?"
    with pytest.raises(ValidationError):
        EvaluationCase.model_validate(
            {
                "knowledge_base_id": "kb-1",
                "query": "query",
                "relevant_chunks": [{"source": "/absolute.md", "chunk_index": -1}],
            }
        )


def test_evaluation_schema_uses_relevant_chunk_objects():
    from app.rag.schemas import EvaluationCase

    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "kb-1",
            "query": "query",
            "relevant_chunks": [{"source": "docs/a.md", "chunk_index": 0}],
        }
    )

    assert case.relevant_chunks[0].source == "docs/a.md"


def test_load_eval_dataset_parses_nonempty_jsonl_lines(tmp_path):
    from app.rag.evaluate import load_eval_dataset

    dataset = tmp_path / "evaluation.jsonl"
    dataset.write_text(
        '\n{"knowledge_base_id":"kb-1","query":"first","relevant_chunks":[{"source":"docs/a.md","chunk_index":0}]}\n\n'
        '{"knowledge_base_id":"kb-2","query":"second","relevant_chunks":[{"source":"docs/b.md","chunk_index":1}]}\n',
        encoding="utf-8",
    )

    cases = load_eval_dataset(dataset)

    assert [(case.knowledge_base_id, case.query) for case in cases] == [
        ("kb-1", "first"),
        ("kb-2", "second"),
    ]


def test_load_eval_dataset_reports_jsonl_line_for_invalid_case(tmp_path):
    from app.rag.evaluate import load_eval_dataset

    dataset = tmp_path / "evaluation.jsonl"
    dataset.write_text(
        '{"knowledge_base_id":"kb-1","query":"first","relevant_chunks":[{"source":"docs/a.md","chunk_index":0}]}\n'
        '{"knowledge_base_id":"","query":"second","relevant_chunks":[]}',
        encoding="utf-8",
    )

    with pytest.raises(ValidationError, match=r"line 2"):
        load_eval_dataset(dataset)


def test_load_eval_dataset_rejects_empty_files(tmp_path):
    from app.rag.evaluate import load_eval_dataset

    dataset = tmp_path / "evaluation.jsonl"
    dataset.write_text("\n \n", encoding="utf-8")

    with pytest.raises(ValueError, match="empty"):
        load_eval_dataset(dataset)


class FakeRetrievalService:
    def __init__(self, results):
        self.results = results
        self.calls = []

    async def retrieve(self, **kwargs):
        self.calls.append(kwargs)
        return self.results


@pytest.mark.anyio
async def test_evaluate_uses_source_and_scores_single_relevant_chunk():
    from app.rag.evaluate import EvaluationCase, EvaluationService

    retrieval_service = FakeRetrievalService([_result("docs/intro.md", 2)])
    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "kb-1",
            "query": "intro",
            "relevant_chunks": [{"source": "docs/intro.md", "chunk_index": 2}],
        }
    )

    metrics = await EvaluationService(retrieval_service).evaluate(
        cases=[case], top_k=1, rerank=False
    )

    assert metrics == {
        "recall@1": 1.0,
        "mrr@1": 1.0,
        "queries": 1,
        "rerank": False,
    }
    assert retrieval_service.calls == [
        {
            "query": "intro",
            "knowledge_base_id": "kb-1",
            "retrieve_top_k": 20,
            "final_top_k": 1,
            "rerank": False,
        }
    ]


@pytest.mark.anyio
async def test_evaluate_deduplicates_retrieved_chunks_and_scores_multiple_relevant():
    from app.rag.evaluate import EvaluationCase, EvaluationService

    retrieval_service = FakeRetrievalService(
        [_result("docs/a.md", 0), _result("docs/a.md", 0), _result("docs/b.md", 1)]
    )
    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "kb-1",
            "query": "query",
            "relevant_chunks": [
                {"source": "docs/a.md", "chunk_index": 0},
                {"source": "docs/b.md", "chunk_index": 1},
            ],
        }
    )

    metrics = await EvaluationService(retrieval_service).evaluate(
        cases=[case],
        top_k=3,
        rerank=True,
        include_failed_queries=True,
    )

    assert metrics == {
        "recall@3": 1.0,
        "mrr@3": 1.0,
        "queries": 1,
        "rerank": True,
        "failed_queries": [],
    }


@pytest.mark.anyio
async def test_evaluate_returns_zero_for_case_without_retrieved_relevant_chunk():
    from app.rag.evaluate import EvaluationCase, EvaluationService

    retrieval_service = FakeRetrievalService([_result("docs/other.md", 0)])
    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "kb-1",
            "query": "query",
            "relevant_chunks": [{"source": "docs/a.md", "chunk_index": 0}],
        }
    )

    metrics = await EvaluationService(retrieval_service).evaluate(
        cases=[case], top_k=1, rerank=False
    )

    assert metrics == {"recall@1": 0.0, "mrr@1": 0.0, "queries": 1, "rerank": False}


@pytest.mark.anyio
async def test_evaluate_includes_structured_partial_recall_details_when_requested():
    from app.rag.evaluate import EvaluationCase, EvaluationService

    retrieval_service = FakeRetrievalService(
        [_result("docs/a.md", 0), _result("docs/other.md", 4)]
    )
    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "kb-1",
            "query": "query",
            "relevant_chunks": [
                {"source": "docs/a.md", "chunk_index": 0},
                {"source": "docs/b.md", "chunk_index": 1},
            ],
        }
    )

    metrics = await EvaluationService(retrieval_service).evaluate(
        cases=[case],
        top_k=2,
        rerank=False,
        include_failed_queries=True,
    )

    assert metrics["failed_queries"] == [
        {
            "query": "query",
            "expected": [
                {"source": "docs/a.md", "chunk_index": 0},
                {"source": "docs/b.md", "chunk_index": 1},
            ],
            "retrieved": [
                {
                    "rank": 1,
                    "source": "docs/a.md",
                    "chunk_index": 0,
                    "vector_score": 0.7,
                    "rerank_score": None,
                },
                {
                    "rank": 2,
                    "source": "docs/other.md",
                    "chunk_index": 4,
                    "vector_score": 0.7,
                    "rerank_score": None,
                },
            ],
        }
    ]


@pytest.mark.anyio
async def test_evaluate_macro_averages_recall_and_reciprocal_rank():
    from app.rag.evaluate import EvaluationCase, EvaluationService

    class PerQueryRetrievalService:
        async def retrieve(self, **kwargs):
            if kwargs["query"] == "first":
                return [
                    _result("docs/irrelevant.md", 0),
                    _result("docs/a.md", 1),
                ]
            return [_result("docs/irrelevant.md", 0)]

    cases = [
        EvaluationCase.model_validate(
            {
                "knowledge_base_id": "kb-1",
                "query": "first",
                "relevant_chunks": [{"source": "docs/a.md", "chunk_index": 1}],
            }
        ),
        EvaluationCase.model_validate(
            {
                "knowledge_base_id": "kb-1",
                "query": "second",
                "relevant_chunks": [{"source": "docs/b.md", "chunk_index": 2}],
            }
        ),
    ]

    metrics = await EvaluationService(PerQueryRetrievalService()).evaluate(
        cases=cases,
        top_k=2,
        rerank=False,
    )

    assert metrics == {
        "recall@2": 0.5,
        "mrr@2": 0.25,
        "queries": 2,
        "rerank": False,
    }


@pytest.mark.anyio
async def test_evaluate_rejects_empty_cases_and_invalid_top_k():
    from app.rag.evaluate import EvaluationService

    service = EvaluationService(FakeRetrievalService([]))

    with pytest.raises(ValueError, match="cases"):
        await service.evaluate(cases=[], top_k=1, rerank=False)
    with pytest.raises(ValueError, match="top_k"):
        await service.evaluate(cases=[object()], top_k=21, rerank=False)


class FakeComparisonRetrievalService:
    def __init__(self, results_by_query_and_mode, has_index=True):
        self.results_by_query_and_mode = results_by_query_and_mode
        self.has_index = has_index
        self.embedded_queries = []
        self.search_calls = []
        self.events = []

    async def has_hnsw_index(self):
        self.events.append("index")
        return self.has_index

    async def embed_query(self, query):
        self.events.append(f"embed:{query}")
        self.embedded_queries.append(query)
        return [float(len(self.embedded_queries))]

    async def benchmark_by_embedding(self, **kwargs):
        self.events.append(f"search:{kwargs['mode']}")
        self.search_calls.append(kwargs)
        query_number = int(kwargs["embedding"][0])
        results, latency_ms = self.results_by_query_and_mode[(query_number, kwargs["mode"])]
        return results, latency_ms


@pytest.mark.anyio
async def test_compare_reuses_embeddings_and_reports_quality_and_database_p95():
    from app.rag.evaluate import EvaluationCase, EvaluationService
    from app.rag.vector_store import SearchMode

    cases = [
        EvaluationCase.model_validate(
            {
                "knowledge_base_id": "kb-1",
                "query": "first",
                "relevant_chunks": [{"source": "docs/a.md", "chunk_index": 0}],
            }
        ),
        EvaluationCase.model_validate(
            {
                "knowledge_base_id": "kb-1",
                "query": "second",
                "relevant_chunks": [{"source": "docs/b.md", "chunk_index": 0}],
            }
        ),
    ]
    service = FakeComparisonRetrievalService(
        {
            (1, SearchMode.EXACT): ([_result("docs/a.md", 0), _result("docs/x.md", 0)], 10.0),
            (1, SearchMode.HNSW): ([_result("docs/x.md", 0), _result("docs/a.md", 0)], 2.0),
            (2, SearchMode.EXACT): ([_result("docs/x.md", 0), _result("docs/b.md", 0)], 20.0),
            (2, SearchMode.HNSW): ([_result("docs/x.md", 0)], 3.0),
        }
    )

    response = await EvaluationService(service).compare_exact_and_hnsw(cases)

    assert response.model_dump() == {
        "queries": 2,
        "exact": {
            "recall_at_5": 1.0,
            "recall_at_10": 1.0,
            "mrr_at_5": 0.75,
            "mrr_at_10": 0.75,
            "p95_latency_ms": 20.0,
        },
        "hnsw": {
            "recall_at_5": 0.5,
            "recall_at_10": 0.5,
            "mrr_at_5": 0.25,
            "mrr_at_10": 0.25,
            "p95_latency_ms": 3.0,
        },
    }
    assert service.embedded_queries == ["first", "second"]
    assert service.events[:3] == ["embed:first", "embed:second", "index"]
    assert [call["mode"] for call in service.search_calls] == [
        SearchMode.EXACT,
        SearchMode.HNSW,
        SearchMode.HNSW,
        SearchMode.EXACT,
    ]
    assert all(call["top_k"] == 10 for call in service.search_calls)


@pytest.mark.anyio
async def test_compare_rejects_missing_hnsw_index():
    from app.rag.evaluate import EvaluationCase, EvaluationService, HnswIndexNotFoundError

    case = EvaluationCase.model_validate(
        {
            "knowledge_base_id": "kb-1",
            "query": "query",
            "relevant_chunks": [{"source": "docs/a.md", "chunk_index": 0}],
        }
    )

    with pytest.raises(HnswIndexNotFoundError):
        await EvaluationService(FakeComparisonRetrievalService({}, has_index=False)).compare_exact_and_hnsw([case])


def test_cli_parses_arguments_and_writes_json(monkeypatch, capsys, tmp_path):
    from app.rag import evaluate

    dataset = tmp_path / "evaluation.jsonl"
    calls = []

    async def fake_run_evaluation(path, top_k, rerank):
        calls.append((path, top_k, rerank))
        return {"recall@5": 0.5, "mrr@5": 0.25, "queries": 2, "rerank": True}

    monkeypatch.setattr(evaluate, "run_evaluation", fake_run_evaluation)

    assert evaluate.main([str(dataset), "--top-k", "5", "--rerank"]) == 0
    assert calls == [(dataset, 5, True)]
    assert json.loads(capsys.readouterr().out) == {
        "recall@5": 0.5,
        "mrr@5": 0.25,
        "queries": 2,
        "rerank": True,
    }


@pytest.mark.anyio
async def test_run_evaluation_closes_runtime_resources(monkeypatch, tmp_path):
    from app.rag import evaluate

    events = []

    class FakeHttpClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            events.append("http_closed")

    class FakeEngine:
        async def dispose(self):
            events.append("engine_disposed")

    class FakeSession:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            events.append("session_closed")

    class FakeEvaluationService:
        def __init__(self, retrieval_service):
            self.retrieval_service = retrieval_service

        async def evaluate(self, cases, top_k, rerank):
            assert cases == ["case"]
            assert (top_k, rerank) == (5, False)
            return {"recall@5": 1.0, "mrr@5": 1.0, "queries": 1, "rerank": False}

    settings = SimpleNamespace(
        ollama_read_timeout=120.0,
        ollama_connect_timeout=5.0,
        ollama_base_url="http://ollama",
        rag_database_url="postgresql+asyncpg://rag",
        rag_embedding_model="embedding",
        rag_reranker_model="reranker",
    )
    engine = FakeEngine()
    monkeypatch.setattr(evaluate, "get_settings", lambda: settings)
    monkeypatch.setattr(evaluate.httpx, "AsyncClient", lambda **kwargs: FakeHttpClient())
    monkeypatch.setattr(evaluate, "create_async_engine", lambda url: engine)
    monkeypatch.setattr(evaluate, "create_session_factory", lambda _: lambda: FakeSession())
    monkeypatch.setattr(evaluate, "load_eval_dataset", lambda _: ["case"])
    monkeypatch.setattr(evaluate, "EmbeddingService", lambda *args: ("embedder", args))
    monkeypatch.setattr(evaluate, "PgVectorStore", lambda session: ("store", session))
    monkeypatch.setattr(evaluate, "BgeReranker", lambda model: ("reranker", model))
    monkeypatch.setattr(evaluate, "RetrievalService", lambda **kwargs: ("retrieval", kwargs))
    monkeypatch.setattr(evaluate, "EvaluationService", FakeEvaluationService)

    metrics = await evaluate.run_evaluation(tmp_path / "evaluation.jsonl", 5, False)

    assert metrics == {
        "recall@5": 1.0,
        "mrr@5": 1.0,
        "queries": 1,
        "rerank": False,
    }
    assert events == ["session_closed", "http_closed", "engine_disposed"]

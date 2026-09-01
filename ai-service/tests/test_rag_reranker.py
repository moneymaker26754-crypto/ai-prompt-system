import importlib
import sys
from types import SimpleNamespace
from uuid import uuid4

import pytest

import app.rag.reranker as reranker_module
from app.core.exceptions import ModelUnavailableError
from app.rag.vector_store import SearchResult


def _candidate(content: str) -> SearchResult:
    return SearchResult(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content=content,
        source="docs/intro.md",
        file_name="intro.md",
        chunk_index=1,
        char_start=0,
        char_end=len(content),
        vector_score=0.4,
    )


@pytest.mark.anyio
async def test_reranker_loads_once_lazily_on_cpu_and_returns_search_results(monkeypatch):
    constructed = []

    class RecordingFlagReranker:
        def __init__(self, model_name, use_fp16):
            constructed.append((model_name, use_fp16))

        def compute_score(self, pairs, normalize):
            assert pairs == [["question", "first"], ["question", "second"]]
            assert normalize is True
            return [0.2, 0.8]

    monkeypatch.setitem(
        sys.modules,
        "FlagEmbedding",
        SimpleNamespace(FlagReranker=RecordingFlagReranker),
    )
    module = importlib.reload(reranker_module)
    reranker = module.BgeReranker("bge-reranker")

    assert constructed == []
    results = await reranker.rerank(
        query="question",
        candidates=[_candidate("first"), _candidate("second")],
        top_k=1,
    )

    assert constructed == [("bge-reranker", False)]
    assert len(results) == 1
    assert isinstance(results[0], SearchResult)
    assert results[0].content == "second"
    assert results[0].vector_score == 0.4
    assert results[0].rerank_score == 0.8


@pytest.mark.anyio
async def test_reranker_wraps_model_load_errors_as_unavailable(monkeypatch):
    class BrokenFlagReranker:
        def __init__(self, model_name, use_fp16):
            raise RuntimeError("model download failed")

    monkeypatch.setitem(
        sys.modules,
        "FlagEmbedding",
        SimpleNamespace(FlagReranker=BrokenFlagReranker),
    )
    module = importlib.reload(reranker_module)

    with pytest.raises(ModelUnavailableError):
        await module.BgeReranker("bge-reranker").rerank(
            query="question",
            candidates=[_candidate("first")],
        )


@pytest.mark.anyio
async def test_reranker_returns_no_result_without_loading_a_model_for_empty_candidates(
    monkeypatch,
):
    class BrokenFlagReranker:
        def __init__(self, model_name, use_fp16):
            raise RuntimeError("model should not be loaded")

    monkeypatch.setitem(
        sys.modules,
        "FlagEmbedding",
        SimpleNamespace(FlagReranker=BrokenFlagReranker),
    )
    module = importlib.reload(reranker_module)

    assert await module.BgeReranker("bge-reranker").rerank(
        query="question", candidates=[]
    ) == []


@pytest.mark.anyio
async def test_reranker_caches_an_initial_model_load_failure(monkeypatch):
    attempts = []

    class BrokenFlagReranker:
        def __init__(self, model_name, use_fp16):
            attempts.append((model_name, use_fp16))
            raise RuntimeError("model download failed")

    monkeypatch.setitem(
        sys.modules,
        "FlagEmbedding",
        SimpleNamespace(FlagReranker=BrokenFlagReranker),
    )
    module = importlib.reload(reranker_module)
    reranker = module.BgeReranker("bge-reranker")

    with pytest.raises(ModelUnavailableError):
        await reranker.rerank(query="one", candidates=[_candidate("first")])
    with pytest.raises(ModelUnavailableError):
        await reranker.rerank(query="two", candidates=[_candidate("second")])

    assert attempts == [("bge-reranker", False)]


@pytest.mark.anyio
async def test_reranker_maps_score_count_mismatch_to_model_unavailable(monkeypatch):
    class MismatchedScoresReranker:
        def __init__(self, model_name, use_fp16):
            pass

        def compute_score(self, pairs, normalize):
            return [0.8]

    monkeypatch.setitem(
        sys.modules,
        "FlagEmbedding",
        SimpleNamespace(FlagReranker=MismatchedScoresReranker),
    )
    module = importlib.reload(reranker_module)

    with pytest.raises(ModelUnavailableError):
        await module.BgeReranker("bge-reranker").rerank(
            query="question",
            candidates=[_candidate("first"), _candidate("second")],
        )

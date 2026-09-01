from uuid import uuid4

import pytest

from app.core.exceptions import ModelUnavailableError
from app.rag.retrieval_service import RetrievalService
from app.rag.vector_store import SearchMode, SearchResult


def _result(content: str) -> SearchResult:
    return SearchResult(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content=content,
        source="docs/intro.md",
        file_name="intro.md",
        chunk_index=0,
        char_start=0,
        char_end=len(content),
        vector_score=0.9,
    )


class FakeEmbedder:
    async def embed_query(self, query: str) -> list[float]:
        self.query = query
        return [0.2] * 1024


class FakeVectorStore:
    def __init__(self, results: list[SearchResult]):
        self.results = results

    async def search(self, **kwargs) -> list[SearchResult]:
        self.kwargs = kwargs
        return self.results


class UnavailableReranker:
    async def rerank(self, **kwargs):
        raise ModelUnavailableError("reranker model is unavailable")


@pytest.mark.anyio
async def test_retrieve_without_reranking_uses_default_candidates_and_final_slice():
    candidates = [_result("first"), _result("second"), _result("third")]
    embedder = FakeEmbedder()
    vector_store = FakeVectorStore(candidates)

    results = await RetrievalService(embedder, vector_store).retrieve(
        query="how to start",
        knowledge_base_id="kb-1",
        final_top_k=2,
        rerank=False,
    )

    assert embedder.query == "how to start"
    assert vector_store.kwargs == {
        "embedding": [0.2] * 1024,
        "knowledge_base_id": "kb-1",
        "top_k": 20,
    }
    assert results == candidates[:2]


@pytest.mark.anyio
async def test_retrieve_keeps_final_top_k_within_vector_candidate_count():
    vector_store = FakeVectorStore([_result("only")])

    await RetrievalService(FakeEmbedder(), vector_store).retrieve(
        query="query",
        knowledge_base_id="kb-1",
        retrieve_top_k=2,
        final_top_k=5,
        rerank=False,
    )

    assert vector_store.kwargs["top_k"] == 5


@pytest.mark.anyio
async def test_retrieve_propagates_reranker_unavailability_without_vector_fallback():
    candidates = [_result("first")]
    reranker = UnavailableReranker()

    with pytest.raises(ModelUnavailableError):
        await RetrievalService(
            FakeEmbedder(), FakeVectorStore(candidates), reranker
        ).retrieve(
            query="query",
            knowledge_base_id="kb-1",
            rerank=True,
        )


@pytest.mark.anyio
async def test_retrieve_by_embedding_forwards_benchmark_mode_without_embedding_again():
    embedder = FakeEmbedder()
    vector_store = FakeVectorStore([_result("first")])
    service = RetrievalService(embedder, vector_store)

    embedding = await service.embed_query("benchmark query")
    results = await service.retrieve_by_embedding(
        embedding=embedding,
        knowledge_base_id="kb-1",
        top_k=10,
        mode=SearchMode.HNSW,
    )

    assert results[0].content == "first"
    assert embedder.query == "benchmark query"
    assert vector_store.kwargs == {
        "embedding": [0.2] * 1024,
        "knowledge_base_id": "kb-1",
        "top_k": 10,
        "mode": SearchMode.HNSW,
    }


@pytest.mark.anyio
async def test_benchmark_by_embedding_returns_store_latency():
    class BenchmarkVectorStore(FakeVectorStore):
        async def benchmark_search(self, **kwargs):
            self.kwargs = kwargs
            return self.results, 7.25

    vector_store = BenchmarkVectorStore([_result("first")])

    results, latency_ms = await RetrievalService(
        FakeEmbedder(), vector_store
    ).benchmark_by_embedding(
        embedding=[0.4] * 1024,
        knowledge_base_id="kb-1",
        top_k=10,
        mode=SearchMode.EXACT,
    )

    assert results[0].content == "first"
    assert latency_ms == 7.25
    assert vector_store.kwargs["mode"] is SearchMode.EXACT

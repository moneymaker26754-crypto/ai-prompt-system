from uuid import uuid4

import pytest

from app.core.exceptions import ModelUnavailableError
from app.rag.retrieval.retrieval_service import RetrievalService
from app.rag.retriever import RetrievalCandidate, RetrievalQuery


def _candidate(content: str) -> RetrievalCandidate:
    return RetrievalCandidate(
        chunk_id=uuid4(),
        document_id=uuid4(),
        content=content,
        source="docs/intro.md",
        file_name="intro.md",
        chunk_index=0,
        char_start=0,
        char_end=len(content),
        fusion_score=0.5,
    )


class _HybridRetriever:
    def __init__(self, results):
        self.results = results

    async def retrieve(self, request: RetrievalQuery, top_k: int):
        self.request = request
        self.top_k = top_k
        return self.results


class _Reranker:
    async def rerank(self, **kwargs):
        self.kwargs = kwargs
        return list(reversed(kwargs["candidates"]))[: kwargs["top_k"]]


@pytest.mark.anyio
async def test_retrieve_defaults_to_hybrid_top_20_and_reranked_top_5() -> None:
    candidates = [_candidate(str(index)) for index in range(8)]
    hybrid = _HybridRetriever(candidates)
    reranker = _Reranker()

    results = await RetrievalService(hybrid, reranker).retrieve(
        query="how to start",
        knowledge_base_id="kb-1",
    )

    assert hybrid.request == RetrievalQuery(text="how to start", knowledge_base_id="kb-1")
    assert hybrid.top_k == 20
    assert reranker.kwargs == {
        "query": "how to start",
        "candidates": candidates,
        "top_k": 5,
    }
    assert results == list(reversed(candidates))[:5]


@pytest.mark.anyio
async def test_retrieve_without_reranking_slices_fused_candidates() -> None:
    candidates = [_candidate("first"), _candidate("second"), _candidate("third")]
    hybrid = _HybridRetriever(candidates)

    results = await RetrievalService(hybrid, None).retrieve(
        query="query",
        knowledge_base_id="kb-1",
        retrieve_top_k=2,
        final_top_k=3,
        rerank=False,
    )

    assert hybrid.top_k == 3
    assert results == candidates


@pytest.mark.anyio
async def test_retrieve_requires_reranker_when_reranking_is_enabled() -> None:
    service = RetrievalService(_HybridRetriever([_candidate("first")]), None)

    with pytest.raises(ModelUnavailableError, match="unavailable"):
        await service.retrieve(query="query", knowledge_base_id="kb-1")

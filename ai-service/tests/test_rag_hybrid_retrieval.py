import asyncio
from dataclasses import replace
from uuid import UUID

import pytest

from app.rag.retrieval.fusion import reciprocal_rank_fusion
from app.rag.retrieval.hybrid_retriever import HybridRetriever
from app.rag.retriever import RetrievalCandidate, RetrievalQuery


def _candidate(value: int, **scores) -> RetrievalCandidate:
    return RetrievalCandidate(
        chunk_id=UUID(int=value),
        document_id=UUID(int=100 + value),
        content=f"chunk-{value}",
        source=f"docs/{value}.md",
        file_name=f"{value}.md",
        chunk_index=value,
        char_start=0,
        char_end=7,
        **scores,
    )


def test_rrf_merges_duplicate_candidates_without_mutating_inputs() -> None:
    dense = _candidate(1, vector_score=0.9)
    keyword = replace(dense, vector_score=None, keyword_score=0.8)

    fused = reciprocal_rank_fusion([[dense], [keyword]])

    assert len(fused) == 1
    assert fused[0].vector_score == 0.9
    assert fused[0].keyword_score == 0.8
    assert fused[0].fusion_score == pytest.approx(2 / 61)
    assert dense.fusion_score is None
    assert keyword.fusion_score is None


def test_rrf_uses_chunk_id_to_make_equal_scores_deterministic() -> None:
    fused = reciprocal_rank_fusion([[_candidate(2), _candidate(1)]])

    assert [candidate.chunk_id for candidate in fused] == [UUID(int=2), UUID(int=1)]

    tied = reciprocal_rank_fusion([[_candidate(2)], [_candidate(1)]])
    assert [candidate.chunk_id for candidate in tied] == [UUID(int=1), UUID(int=2)]


class _ConcurrentRetriever:
    def __init__(self, started: list[str], both_started: asyncio.Event, name: str, result):
        self.started = started
        self.both_started = both_started
        self.name = name
        self.result = result

    async def retrieve(self, request: RetrievalQuery, top_k: int):
        self.request = request
        self.top_k = top_k
        self.started.append(self.name)
        if len(self.started) == 2:
            self.both_started.set()
        await self.both_started.wait()
        return self.result


@pytest.mark.anyio
async def test_hybrid_retriever_runs_both_retrievers_concurrently_and_limits_results() -> None:
    started: list[str] = []
    both_started = asyncio.Event()
    dense = _ConcurrentRetriever(started, both_started, "dense", [_candidate(1), _candidate(2)])
    keyword = _ConcurrentRetriever(started, both_started, "keyword", [_candidate(2), _candidate(3)])
    request = RetrievalQuery(text="hybrid query", knowledge_base_id="kb-1")

    results = await asyncio.wait_for(
        HybridRetriever(dense, keyword).retrieve(request, top_k=2),
        timeout=1,
    )

    assert set(started) == {"dense", "keyword"}
    assert dense.request == keyword.request == request
    assert dense.top_k == keyword.top_k == 2
    assert [candidate.chunk_id for candidate in results] == [UUID(int=2), UUID(int=1)]


@pytest.mark.anyio
async def test_hybrid_retriever_cancels_sibling_when_one_retriever_fails() -> None:
    sibling_started = asyncio.Event()
    sibling_cancelled = asyncio.Event()

    class _FailingRetriever:
        async def retrieve(self, request: RetrievalQuery, top_k: int):
            await sibling_started.wait()
            raise RuntimeError("dense failed")

    class _BlockingRetriever:
        async def retrieve(self, request: RetrievalQuery, top_k: int):
            sibling_started.set()
            try:
                await asyncio.Event().wait()
            except asyncio.CancelledError:
                sibling_cancelled.set()
                raise

    with pytest.raises(RuntimeError, match="dense failed"):
        await HybridRetriever(
            _FailingRetriever(),
            _BlockingRetriever(),
        ).retrieve(
            RetrievalQuery(text="query", knowledge_base_id="kb-1"),
            top_k=20,
        )

    assert sibling_cancelled.is_set()

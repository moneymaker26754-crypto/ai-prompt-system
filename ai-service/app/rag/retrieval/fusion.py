from dataclasses import replace
from uuid import UUID

from app.rag.retriever import RetrievalCandidate


def reciprocal_rank_fusion(
    result_lists: list[list[RetrievalCandidate]],
    k: int = 60,
) -> list[RetrievalCandidate]:
    candidates: dict[UUID, RetrievalCandidate] = {}
    scores: dict[UUID, float] = {}

    for results in result_lists:
        for rank, candidate in enumerate(results, start=1):
            existing = candidates.get(candidate.chunk_id)
            candidates[candidate.chunk_id] = (
                candidate if existing is None else _merge_scores(existing, candidate)
            )
            scores[candidate.chunk_id] = (
                scores.get(candidate.chunk_id, 0.0) + 1.0 / (k + rank)
            )

    fused = [
        replace(candidate, fusion_score=scores[chunk_id])
        for chunk_id, candidate in candidates.items()
    ]
    return sorted(
        fused,
        key=lambda candidate: (-(candidate.fusion_score or 0.0), candidate.chunk_id.int),
    )


def _merge_scores(
    first: RetrievalCandidate,
    second: RetrievalCandidate,
) -> RetrievalCandidate:
    return replace(
        first,
        vector_score=(
            first.vector_score if first.vector_score is not None else second.vector_score
        ),
        keyword_score=(
            first.keyword_score if first.keyword_score is not None else second.keyword_score
        ),
        rerank_score=(
            first.rerank_score if first.rerank_score is not None else second.rerank_score
        ),
    )

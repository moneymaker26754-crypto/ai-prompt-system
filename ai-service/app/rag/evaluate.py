import argparse
import asyncio
import json
from math import ceil
from pathlib import Path
from typing import Any

import httpx
from pydantic import ValidationError

from app.clients.ollama import OllamaClient
from app.core.config import get_settings
from app.database import create_async_engine, create_session_factory
from app.rag.embedder import EmbeddingService
from app.rag.reranker import BgeReranker
from app.rag.retrieval_service import RetrievalService
from app.rag.schemas import (
    EvaluationCase,
    EvaluationRetrievedChunk,
    FailedEvaluationQuery,
    RagComparisonResponse,
    RetrievalBenchmarkMetrics,
)
from app.rag.vector_store import PgVectorStore, SearchMode, SearchResult


class HnswIndexNotFoundError(RuntimeError):
    pass


def load_eval_dataset(path: Path) -> list[EvaluationCase]:
    cases: list[EvaluationCase] = []

    with path.open(encoding="utf-8") as dataset:
        for line_number, line in enumerate(dataset, start=1):
            if not line.strip():
                continue
            try:
                cases.append(EvaluationCase.model_validate_json(line))
            except ValidationError as exc:
                raise ValidationError.from_exception_data(
                    f"Evaluation case at line {line_number}", exc.errors()
                ) from exc

    if not cases:
        raise ValueError("Evaluation dataset is empty")
    return cases


def resolve_eval_dataset_path(dataset: str, dataset_dir: Path) -> Path:
    relative_path = Path(dataset)
    if relative_path.is_absolute() or relative_path.suffix.lower() != ".jsonl":
        raise ValueError("dataset must be a relative .jsonl path")

    base_path = dataset_dir.resolve()
    dataset_path = (base_path / relative_path).resolve()
    if not dataset_path.is_relative_to(base_path):
        raise ValueError("dataset must be inside the configured evaluation directory")
    if not dataset_path.is_file():
        raise FileNotFoundError(dataset)
    return dataset_path


class EvaluationService:
    def __init__(self, retrieval_service: RetrievalService):
        self.retrieval_service = retrieval_service

    async def evaluate(
        self,
        cases: list[EvaluationCase],
        top_k: int,
        rerank: bool,
        include_failed_queries: bool = False,
    ) -> dict[str, Any]:
        if not cases:
            raise ValueError("Evaluation cases must not be empty")
        if isinstance(top_k, bool) or not isinstance(top_k, int) or not 1 <= top_k <= 20:
            raise ValueError("top_k must be between 1 and 20")

        recalls: list[float] = []
        reciprocal_ranks: list[float] = []
        failed_queries: list[dict[str, Any]] = []
        for case in cases:
            results = await self.retrieval_service.retrieve(
                query=case.query,
                knowledge_base_id=case.knowledge_base_id,
                retrieve_top_k=max(20, top_k),
                final_top_k=top_k,
                rerank=rerank,
            )
            relevant = {(chunk.source, chunk.chunk_index) for chunk in case.relevant_chunks}
            retrieved = [(result.source, result.chunk_index) for result in results]
            recall = len(set(retrieved) & relevant) / len(relevant)
            recalls.append(recall)

            reciprocal_rank = 0.0
            for rank, chunk in enumerate(retrieved, start=1):aswed
                if chunk in relevant:
                    reciprocal_rank = 1.0 / rank
                    break
            reciprocal_ranks.append(reciprocal_rank)

            if include_failed_queries and recall < 1.0:
                failure = FailedEvaluationQuery(
                    query=case.query,
                    expected=case.relevant_chunks,
                    retrieved=[
                        EvaluationRetrievedChunk(
                            rank=rank,
                            source=result.source,
                            chunk_index=result.chunk_index,
                            vector_score=result.vector_score,
                            rerank_score=result.rerank_score,
                        )
                        for rank, result in enumerate(results, start=1)
                    ],
                )
                failed_queries.append(failure.model_dump())

        metrics: dict[str, Any] = {
            f"recall@{top_k}": round(sum(recalls) / len(recalls), 4),
            f"mrr@{top_k}": round(
                sum(reciprocal_ranks) / len(reciprocal_ranks),
                4,
            ),
            "queries": len(cases),
            "rerank": rerank,
        }
        if include_failed_queries:
            metrics["failed_queries"] = failed_queries
        return metrics

    async def compare_exact_and_hnsw(
        self,
        cases: list[EvaluationCase],
    ) -> RagComparisonResponse:
        if not cases:
            raise ValueError("Evaluation cases must not be empty")
        embeddings = [
            await self.retrieval_service.embed_query(case.query)
            for case in cases
        ]
        if not await self.retrieval_service.has_hnsw_index():
            raise HnswIndexNotFoundError(
                "HNSW index rag_chunk_embedding_hnsw is not available"
            )

        result_sets: dict[SearchMode, list[list[SearchResult]]] = {
            SearchMode.EXACT: [],
            SearchMode.HNSW: [],
        }
        latencies_ms: dict[SearchMode, list[float]] = {
            SearchMode.EXACT: [],
            SearchMode.HNSW: [],
        }

        for index, (case, embedding) in enumerate(
            zip(cases, embeddings, strict=True)
        ):
            modes = (
                (SearchMode.EXACT, SearchMode.HNSW)
                if index % 2 == 0
                else (SearchMode.HNSW, SearchMode.EXACT)
            )
            for mode in modes:
                results, latency_ms = await self.retrieval_service.benchmark_by_embedding(
                    embedding=embedding,
                    knowledge_base_id=case.knowledge_base_id,
                    top_k=10,
                    mode=mode,
                )
                result_sets[mode].append(results)
                latencies_ms[mode].append(latency_ms)

        return RagComparisonResponse(
            queries=len(cases),
            exact=_calculate_metrics(
                cases,
                result_sets[SearchMode.EXACT],
                latencies_ms[SearchMode.EXACT],
            ),
            hnsw=_calculate_metrics(
                cases,
                result_sets[SearchMode.HNSW],
                latencies_ms[SearchMode.HNSW],
            ),
        )


def _calculate_metrics(
    cases: list[EvaluationCase],
    result_sets: list[list[SearchResult]],
    latencies_ms: list[float],
) -> RetrievalBenchmarkMetrics:
    values: dict[str, float] = {}
    for top_k in (5, 10):
        recalls: list[float] = []
        reciprocal_ranks: list[float] = []
        for case, results in zip(cases, result_sets, strict=True):
            relevant = {(chunk.source, chunk.chunk_index) for chunk in case.relevant_chunks}
            retrieved = [
                (result.source, result.chunk_index)
                for result in results[:top_k]
            ]
            recalls.append(len(set(retrieved) & relevant) / len(relevant))
            reciprocal_ranks.append(
                next(
                    (
                        1.0 / rank
                        for rank, chunk in enumerate(retrieved, start=1)
                        if chunk in relevant
                    ),
                    0.0,
                )
            )
        values[f"recall_at_{top_k}"] = round(sum(recalls) / len(recalls), 4)
        values[f"mrr_at_{top_k}"] = round(
            sum(reciprocal_ranks) / len(reciprocal_ranks),
            4,
        )

    return RetrievalBenchmarkMetrics(
        **values,
        p95_latency_ms=round(_nearest_rank_p95(latencies_ms), 3),
    )


def _nearest_rank_p95(values: list[float]) -> float:
    if not values:
        raise ValueError("Latency samples must not be empty")
    ordered = sorted(values)
    return ordered[ceil(0.95 * len(ordered)) - 1]


async def run_evaluation(path: Path, top_k: int, rerank: bool) -> dict[str, float | int]:
    cases = load_eval_dataset(path)
    settings = get_settings()
    timeout = httpx.Timeout(
        timeout=settings.ollama_read_timeout,
        connect=settings.ollama_connect_timeout,
    )
    engine = create_async_engine(settings.rag_database_url)
    try:
        async with httpx.AsyncClient(
            base_url=settings.ollama_base_url,
            timeout=timeout,
        ) as http_client:
            ollama_client = OllamaClient(http_client=http_client, settings=settings)
            embedder = EmbeddingService(ollama_client, settings.rag_embedding_model)
            reranker = BgeReranker(settings.rag_reranker_model)
            session_factory = create_session_factory(engine)
            async with session_factory() as session:
                retrieval_service = RetrievalService(
                    embedder=embedder,
                    vector_store=PgVectorStore(session),
                    reranker=reranker,
                )
                return await EvaluationService(retrieval_service).evaluate(
                    cases=cases,
                    top_k=top_k,
                    rerank=rerank,
                )
    finally:
        await engine.dispose()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Evaluate RAG retrieval against JSONL cases")
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--top-k", type=int, default=5, choices=range(1, 21))
    parser.add_argument("--rerank", action="store_true")
    args = parser.parse_args(argv)

    metrics = asyncio.run(run_evaluation(args.dataset, args.top_k, args.rerank))
    print(json.dumps(metrics))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

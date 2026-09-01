import asyncio
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from pydantic import ValidationError

from app.api.dependencies import (
    get_evaluation_service,
    get_ingest_service,
    get_retrieval_service,
    require_internal_api_key,
)
from app.core.config import Settings, get_settings
from app.rag.evaluate import (
    EvaluationService,
    HnswIndexNotFoundError,
    load_eval_dataset,
    resolve_eval_dataset_path,
)
from app.rag.ingest_service import IngestService
from app.rag.retrieval_service import RetrievalService
from app.rag.schemas import (
    RagEvaluateRequest,
    RagCompareRequest,
    RagComparisonResponse,
    RagIngestRequest,
    RagSearchRequest,
    RagSearchResponse,
    RagSearchResult,
)

router = APIRouter(
    prefix="/internal/rag",
    tags=["rag"],
    dependencies=[Depends(require_internal_api_key)],
)


@router.post("/ingest")
async def ingest_document(
        request: RagIngestRequest,
        service: IngestService = Depends(
            get_ingest_service
        ),
):
    return await service.ingest(
        Path(request.path),
        request.knowledge_base_id,
    )


@router.post(
    "/search",
    response_model=RagSearchResponse,
)
async def search(
        request: RagSearchRequest,
        service: RetrievalService = Depends(
            get_retrieval_service
        ),
):

    results = await service.retrieve(
        query=request.query,
        knowledge_base_id=request.knowledge_base_id,
        final_top_k=request.top_k,
        rerank=request.rerank,
    )

    return RagSearchResponse(
        query=request.query,

        results=[
            RagSearchResult(
                rank=index,

                chunk_id=str(
                    result.chunk_id
                ),
                document_id=str(result.document_id),
                source=result.source,
                file_name=result.file_name,
                chunk_index=result.chunk_index,
                char_start=result.char_start,
                char_end=result.char_end,
                vector_score=result.vector_score,
                rerank_score=result.rerank_score,
                content=result.content,
            )
            for index, result
            in enumerate(results, start=1)
        ],
    )


@router.post("/evaluate")
async def evaluate(
    request: RagEvaluateRequest,
    service: EvaluationService = Depends(get_evaluation_service),
    settings: Settings = Depends(get_settings),
):
    try:
        dataset_path = resolve_eval_dataset_path(
            request.dataset,
            settings.rag_eval_dataset_dir,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Evaluation dataset not found") from exc

    try:
        cases = await asyncio.to_thread(load_eval_dataset, dataset_path)
    except (ValidationError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    return await service.evaluate(
        cases=cases,
        top_k=request.top_k,
        rerank=request.rerank,
        include_failed_queries=request.include_failed_queries,
    )


@router.post(
    "/evaluate/compare",
    response_model=RagComparisonResponse,
)
async def compare_retrieval(
    request: RagCompareRequest,
    service: EvaluationService = Depends(get_evaluation_service),
    settings: Settings = Depends(get_settings),
):
    try:
        dataset_path = resolve_eval_dataset_path(
            request.dataset,
            settings.rag_eval_dataset_dir,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Evaluation dataset not found") from exc

    try:
        cases = await asyncio.to_thread(load_eval_dataset, dataset_path)
    except (ValidationError, ValueError) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    try:
        return await service.compare_exact_and_hnsw(cases)
    except HnswIndexNotFoundError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc

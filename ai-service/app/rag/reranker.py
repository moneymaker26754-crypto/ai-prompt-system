import asyncio
from dataclasses import replace
from typing import Any

from app.core.exceptions import ModelUnavailableError
from app.rag.vector_store import SearchResult


class BgeReranker:
    def __init__(self, model_name: str) -> None:
        self._model_name = model_name
        self._model: Any | None = None
        self._model_load_error: ModelUnavailableError | None = None
        self._model_lock = asyncio.Lock()

    async def _get_model(self) -> Any:
        if self._model_load_error is not None:
            raise self._model_load_error
        if self._model is not None:
            return self._model

        async with self._model_lock:
            if self._model_load_error is not None:
                raise self._model_load_error
            if self._model is not None:
                return self._model
            try:
                self._model = await asyncio.to_thread(self._load_model)
            except Exception as exc:
                self._model_load_error = ModelUnavailableError(
                    "Reranker model is unavailable"
                )
                raise self._model_load_error from exc
            return self._model

    def _load_model(self) -> Any:
        from FlagEmbedding import FlagReranker

        return FlagReranker(self._model_name, use_fp16=False)

    async def rerank(
            self,
            query: str,
            candidates: list[SearchResult],
            top_k: int = 5,
    ) -> list[SearchResult]:
        if not candidates:
            return []
        model = await self._get_model()
        pairs = [[query, candidate.content] for candidate in candidates]

        try:
            scores = await asyncio.to_thread(
                model.compute_score,
                pairs,
                normalize=True,
            )
        except Exception as exc:
            raise ModelUnavailableError("Reranker model is unavailable") from exc

        if isinstance(scores, (float, int)):
            scores = [scores]
        if len(scores) != len(candidates):
            raise ModelUnavailableError("Reranker model returned invalid scores")

        ranked = sorted(
            zip(candidates, scores, strict=True),
            key=lambda item: item[1],
            reverse=True,
        )
        return [
            replace(candidate, rerank_score=float(score))
            for candidate, score in ranked[:top_k]
        ]

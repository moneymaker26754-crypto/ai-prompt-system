import json
import logging
import time

from pydantic import ValidationError

from app.core.exceptions import (
    AiServiceError,
    InvalidModelOutputError,
)
from app.core.metrics import record_ai_request, status_for_exception
from app.clients.ollama import OllamaClient
from app.core.request_context import get_request_id
from app.prompts.review import build_review_prompt
from app.schemas.review import (
    ReviewRequest,
    ReviewResponse,
)

logger = logging.getLogger(__name__)


class PromptReviewService:
    def __init__(self, ollama_client: OllamaClient,):
        self._ollama_client = ollama_client

    async def review(self, request: ReviewRequest, ) -> ReviewResponse:
        start_time = time.perf_counter()
        request_id = get_request_id()

        prompt = build_review_prompt(original_prompt=request.original_prompt, optimized_prompt=request.optimized_prompt)

        result = None
        try:
            result = await self._ollama_client.generate(prompt=prompt)
            response = parse_review_output(result.response, model=result.model)

            duration_seconds = time.perf_counter() - start_time
            record_ai_request(
                operation="review",
                status="success",
                duration_seconds=duration_seconds,
                input_tokens=result.prompt_eval_count,
                output_tokens=result.eval_count,
            )

            logger.info(
                "ai operation completed",
                extra={
                    "operation": "review",
                    "requestId": request_id,
                    "model": result.model,
                    "durationMs": int(duration_seconds * 1000),
                    "status": "success",
                    "promptLength": len(request.original_prompt),
                    "optimizedPromptLength": len(request.optimized_prompt),
                },
            )

            return response
        except AiServiceError as exc:
            duration_seconds = time.perf_counter() - start_time
            status = status_for_exception(exc)
            record_ai_request(
                operation="review",
                status=status,
                duration_seconds=duration_seconds,
                input_tokens=result.prompt_eval_count if result else None,
                output_tokens=result.eval_count if result else None,
            )
            logger.error(
                "ai operation failed",
                extra={
                    "operation": "review",
                    "requestId": request_id,
                    "status": status,
                    "durationMs": int(duration_seconds * 1000),
                    "errorCode": exc.code,
                },
            )
            raise


def parse_review_output(raw: str, *, model: str,) -> ReviewResponse:
    if not raw or not raw.strip():
        raise InvalidModelOutputError("Review model returned empty output")

    normalized = raw.strip()

    if normalized.startswith("```"):
        lines = normalized.splitlines()

        if lines:
            lines = lines[1:]

        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]

        normalized = "\n".join(lines).strip()

    start = normalized.find("{")
    end = normalized.rfind("}")

    if start == -1 or end <= start:
        raise InvalidModelOutputError("Review model did not return JSON object")

    json_text = normalized[start:end + 1]

    try:
        data = json.loads(json_text)

        return ReviewResponse(**data, model=model)

    except(json.JSONDecodeError, ValidationError, TypeError) as exc:
        raise InvalidModelOutputError("Invalid review model output") from exc

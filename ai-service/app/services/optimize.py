import logging
import time

from app.clients.ollama import OllamaClient
from app.core.exceptions import (
    AiServiceError,
    InvalidModelOutputError,
)
from app.core.metrics import record_ai_request, status_for_exception
from app.core.request_context import get_request_id
from app.prompts.optimize import build_optimize_prompt
from app.schemas.optimize import (
    OptimizeRequest,
    OptimizeResponse,
)

logger = logging.getLogger(__name__)


class PromptOptimizeService:
    def __init__(
            self,
            ollama_client: OllamaClient,
    ):
        self._ollama_client = ollama_client

    async def optimize(
            self,
            request: OptimizeRequest,
    ) -> OptimizeResponse:
        start_time = time.perf_counter()
        request_id = get_request_id()

        prompt = build_optimize_prompt(
            original_prompt=request.original_prompt,
            analysis_result=request.analysis_result,
            instruction=request.instruction,
            target=request.target,
            output_format=request.output_format,
        )

        result = None
        try:
            result = await self._ollama_client.generate(
                prompt=prompt,
                system_prompt=request.system_prompt,
            )

            optimized_prompt = result.response.strip()

            if not optimized_prompt:
                raise InvalidModelOutputError(
                    "Model returned empty optimized prompt"
                )
        except AiServiceError as exc:
            duration_seconds = time.perf_counter() - start_time
            status = status_for_exception(exc)
            record_ai_request(
                operation="optimize",
                status=status,
                duration_seconds=duration_seconds,
                input_tokens=result.prompt_eval_count if result else None,
                output_tokens=result.eval_count if result else None,
            )
            logger.error(
                "ai operation failed",
                extra={
                    "operation": "optimize",
                    "requestId": request_id,
                    "status": status,
                    "durationMs": int(duration_seconds * 1000),
                    "errorCode": exc.code,
                },
            )
            raise

        duration_seconds = time.perf_counter() - start_time
        record_ai_request(
            operation="optimize",
            status="success",
            duration_seconds=duration_seconds,
            input_tokens=result.prompt_eval_count,
            output_tokens=result.eval_count,
        )

        logger.info(
            "ai operation completed",
            extra={
                "operation": "optimize",
                "requestId": request_id,
                "model": result.model,
                "durationMs": int(duration_seconds * 1000),
                "status": "success",
                "promptLength": len(request.original_prompt),
                "optimizedPromptLength": len(optimized_prompt),
            },
        )

        return OptimizeResponse(
            optimized_prompt=optimized_prompt,
            model=result.model,
        )

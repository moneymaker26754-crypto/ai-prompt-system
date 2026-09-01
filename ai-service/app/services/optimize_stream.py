from collections.abc import AsyncIterator
import logging
import time

from app.clients.ollama import OllamaClient
from app.core.exceptions import AiServiceError
from app.core.metrics import record_ai_request, status_for_exception
from app.core.request_context import get_request_id
from app.prompts.optimize import build_optimize_prompt
from app.schemas.optimize import OptimizeRequest
from app.schemas.stream import (
    OptimizeStreamEvent,
    StreamEventType,
)

logger = logging.getLogger(__name__)


class PromptOptimizeStreamService:
    def __init__(self, ollama_client: OllamaClient,):
        self._ollama_client = ollama_client

    async def optimize(self, request: OptimizeRequest) -> AsyncIterator[OptimizeStreamEvent]:
        start_time = time.perf_counter()
        request_id = get_request_id()
        input_tokens = None
        output_tokens = None
        try:
            prompt = build_optimize_prompt(
                original_prompt=request.original_prompt,
                analysis_result=request.analysis_result,
                instruction=request.instruction,
                target=request.target,
                output_format=request.output_format,
            )

            model_name = None
            async for chunk in (
                    self._ollama_client.generate_stream(
                        prompt=prompt,
                        system_prompt=request.system_prompt,
                    )
            ):
                model_name = chunk.model
                input_tokens = chunk.prompt_eval_count or input_tokens
                output_tokens = chunk.eval_count or output_tokens
                if chunk.response:
                    yield OptimizeStreamEvent(
                        type=StreamEventType.TOKEN,
                        content=chunk.response,
                        model=model_name,
                    )
                if chunk.done:
                    yield OptimizeStreamEvent(
                        type=StreamEventType.DONE,
                        model=model_name,
                    )
            duration_seconds = time.perf_counter() - start_time
            record_ai_request(
                operation="optimize",
                status="success",
                duration_seconds=duration_seconds,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
            )
            logger.info(
                "ai stream operation completed",
                extra={
                    "operation": "optimize",
                    "requestId": request_id,
                    "model": model_name,
                    "durationMs": int(duration_seconds * 1000),
                    "status": "success",
                    "promptLength": len(request.original_prompt),
                },
            )
        except AiServiceError as exc:
            duration_seconds = time.perf_counter() - start_time
            status = status_for_exception(exc)
            record_ai_request(
                operation="optimize",
                status=status,
                duration_seconds=duration_seconds,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
            )
            logger.error(
                "ai stream operation failed",
                extra={
                    "operation": "optimize",
                    "requestId": request_id,
                    "durationMs": int(duration_seconds * 1000),
                    "status": status,
                    "errorCode": exc.code,
                },
            )
            yield OptimizeStreamEvent(
                type=StreamEventType.ERROR,
                code=exc.code,
                content=exc.message,
            )

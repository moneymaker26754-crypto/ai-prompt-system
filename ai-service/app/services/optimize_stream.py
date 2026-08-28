from collections.abc import AsyncIterator

from app.clients.ollama import OllamaClient
from app.core.exceptions import AiServiceError
from app.prompts.optimize import build_optimize_prompt
from app.schemas.optimize import OptimizeRequest
from app.schemas.stream import (
    OptimizeStreamEvent,
    StreamEventType,
)


class PromptOptimizeStreamService:
    def __init__(self, ollama_client: OllamaClient,):
        self._ollama_client = ollama_client

    async def optimize(self, request: OptimizeRequest) -> AsyncIterator[OptimizeStreamEvent]:

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
        except AiServiceError as exc:
            yield OptimizeStreamEvent(
                type=StreamEventType.ERROR,
                code=exc.code,
                content=exc.message,
            )
from app.clients.ollama import OllamaClient
from app.core.exceptions import (
    InvalidModelOutputError,
)
from app.prompts.optimize import build_optimize_prompt
from app.schemas.optimize import (
    OptimizeRequest,
    OptimizeResponse,
)


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

        prompt = build_optimize_prompt(
            original_prompt=request.original_prompt,
            analysis_result=request.analysis_result,
            instruction=request.instruction,
            target=request.target,
            output_format=request.output_format,
        )

        result = await self._ollama_client.generate(
            prompt=prompt,
            system_prompt=request.system_prompt,
        )

        optimized_prompt = (
            result.response.strip()
        )

        if not optimized_prompt:
            raise InvalidModelOutputError(
                "Model returned empty optimized prompt"
            )

        return OptimizeResponse(
            optimized_prompt=optimized_prompt,
            model=result.model,
        )
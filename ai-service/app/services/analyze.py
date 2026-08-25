from app.clients.ollama import OllamaClient
from app.core.exceptions import InvalidModelOutputError
from app.prompts.analyze import build_analyze_prompt
from app.schemas.analyze import (
    AnalyzeRequest,
    AnalyzeResponse,
)


class PromptAnalyzeService:
    def __init__(
            self,
            ollama_client: OllamaClient,
    ):
        self._ollama_client = ollama_client

    async def analyze(
            self,
            request: AnalyzeRequest,
    ) -> AnalyzeResponse:
        prompt = build_analyze_prompt(
            request.original_prompt
        )

        result = await self._ollama_client.generate(
            prompt=prompt,
            system_prompt=request.system_prompt,
        )

        analysis = result.response.strip()

        if not analysis:
            raise InvalidModelOutputError(
                "Model returned empty analysis"
            )

        return AnalyzeResponse(
            analysis=analysis,
            model=result.model,
        )
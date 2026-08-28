import json

from pydantic import ValidationError

from app.core.exceptions import (
    InvalidModelOutputError,
)
from app.clients.ollama import OllamaClient
from app.prompts.review import build_review_prompt
from app.schemas.review import (
    ReviewRequest,
    ReviewResponse,
)



class PromptReviewService:
    def __init__(self, ollama_client: OllamaClient,):
        self._ollama_client = ollama_client

    async def review(self, request: ReviewRequest, ) -> ReviewResponse:
        prompt = build_review_prompt(original_prompt=request.original_prompt, optimized_prompt=request.optimized_prompt)

        result = await self._ollama_client.generate(prompt=prompt)

        return parse_review_output(result.response, model=result.model)



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

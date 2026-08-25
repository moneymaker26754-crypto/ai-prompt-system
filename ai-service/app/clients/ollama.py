import httpx
from pydantic import ValidationError

from app.core.config import Settings
from app.core.exceptions import (
    InvalidModelOutputError,
    ModelTimeoutError,
    ModelUnavailableError,
    ModelUpstreamError,
)
from app.schemas.ollama import OllamaGenerateResponse


class OllamaClient:
    def __init__(
            self,
            http_client: httpx.AsyncClient,
            settings: Settings,
    ):
        self._http_client = http_client
        self._settings = settings

    async def generate(
            self,
            prompt: str,
            system_prompt: str | None = None,
    ) -> OllamaGenerateResponse:
        payload = {
            "model": self._settings.ollama_model,
            "prompt": prompt,
            "stream": False,
            "think": False,
        }

        if system_prompt:
            payload["system"] = system_prompt

        try:
            response = await self._http_client.post(
                "/api/generate",
                json=payload,
            )

            response.raise_for_status()

        except httpx.TimeoutException as exc:
            raise ModelTimeoutError(
                "Ollama request timed out"
            ) from exc

        except httpx.ConnectError as exc:
            raise ModelUnavailableError(
                "Unable to connect to Ollama"
            ) from exc

        except httpx.HTTPStatusError as exc:
            raise ModelUpstreamError(
                f"Ollama returned HTTP {exc.response.status_code}"
            ) from exc

        try:
            return OllamaGenerateResponse.model_validate(
                response.json()
            )
        except (ValueError, ValidationError) as exc:
            raise InvalidModelOutputError(
                "Invalid response returned by Ollama"
            ) from exc
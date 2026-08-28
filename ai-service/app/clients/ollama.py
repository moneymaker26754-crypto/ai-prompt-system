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
from collections.abc import AsyncIterator
import json


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

    async def generate_stream(
            self,
            prompt: str,
            system_prompt: str | None = None,
    ) -> AsyncIterator[OllamaGenerateResponse]:

        payload = {
            "model": self._settings.ollama_model,
            "prompt": prompt,
            "stream": True,
            "think": False,
        }

        if system_prompt:
            payload["system"] = system_prompt

        try:
            async with self._http_client.stream(
                "POST",
                "/api/generate",
                json=payload,
            ) as response:

                response.raise_for_status()

                async for line in response.aiter_lines():
                    if not line.strip():
                        continue

                    try:
                        data = json.loads(line)

                        chunk = (
                            OllamaGenerateResponse.model_validate(data)
                        )

                    except(json.JSONDecodeError, ValidationError) as exc:
                        raise InvalidModelOutputError(
                            "Invalid Ollama stream chunk"
                        ) from exc

                    yield chunk

        except httpx.TimeoutException as exc:
            raise ModelTimeoutError(
                "Ollama stream timed out"
            ) from exc

        except httpx.ConnectError as exc:
            raise ModelUnavailableError(
                "Unable to connect to Ollama"
            ) from exc

        except httpx.HTTPStatusError as exc:
            raise ModelUpstreamError(
                f"Ollama return Http " 
                f"{exc.response.status_code}"
            ) from exc


    async def check_ready(self) -> None:
        try:
            response = await self._http_client.get("/api/tags")
            response.raise_for_status()

        except httpx.TimeoutException as exc:
            raise ModelTimeoutError(
                "Ollama readiness check timed out"
            ) from exc

        except httpx.ConnectError as exc:
            raise ModelUnavailableError(
                "Ollama is unavailable"
            ) from exc

        except httpx.HTTPStatusError as exc:
            raise ModelUnavailableError(
                "Unable to connect to Ollama"
            ) from exc
import json

import httpx
import pytest
from sentry_sdk import transport

from app.clients.ollama import OllamaClient
from app.core.config import Settings


@pytest.mark.anyio
async def test_generate_returns_ollama_response():
    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)

        assert request.url.path == "/api/generate"
        assert payload["model"] == "qwen3.5:9b"
        assert payload["prompt"] == "hello"
        assert payload["stream"] is False

        return httpx.Response(
            status_code=200,
            json={
                "model": "qwen3.5:9b",
                "response": "analysis result",
                "done": True,
            },
        )

    transport = httpx.MockTransport(handler)

    async with httpx.AsyncClient(
            transport=transport,
            base_url="http://test",
    ) as http_client:
        client = OllamaClient(
            http_client=http_client,
            settings=Settings(),
        )

        result = await client.generate(
            prompt="hello",
        )

    assert result.response == "analysis result"
    assert result.model == "qwen3.5:9b"


@pytest.mark.anyio
async def test_generate_converts_timeout_error():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout(
            "timeout",
            request=request,
        )

    transport = httpx.MockTransport(handler)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as http_client:
        client = OllamaClient(
            http_client=http_client,
            settings=Settings(),
        )

        from app.core.exceptions import ModelTimeoutError

        with pytest.raises(ModelTimeoutError):
            await client.generate(
                prompt="hello",
            )


@pytest.mark.anyio
async def test_generate_converts_upstream_http_error():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            500,
            request=request,
            json={"error": "model failed"},
        )

    transport = httpx.MockTransport(handler)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as http_client:
        client = OllamaClient(
            http_client=http_client,
            settings=Settings(),
        )

        from app.core.exceptions import ModelUpstreamError

        with pytest.raises(ModelUpstreamError):
            await client.generate(
                prompt="hello",
    )




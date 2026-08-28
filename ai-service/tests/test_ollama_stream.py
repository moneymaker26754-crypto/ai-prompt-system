import httpx
import pytest

from app.clients.ollama import OllamaClient
from app.core.config import Settings


@pytest.mark.asyncio
async def test_generate_stream_yields_token():
    body = (
        '{"model":"qwen3.5:9b","response":"你","done":false}\n'
        '{"model":"qwen3.5:9b","response":"好","done":false}\n'
        '{"model":"qwen3.5:9b","response":"","done":true}\n'
    )

    def handler(request: httpx.Request):
        return httpx.Response(
            status_code=200,
            text=body,
            headers={
                "content-type": "application/x-ndjson"
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

        chunks = []

        async for chunk in client.generate_stream(
            prompt="test",
        ):
            chunks.append(chunk)

    assert chunks[0].response == "你"
    assert chunks[1].response == "好"
    assert chunks[-1].done is True
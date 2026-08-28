import pytest

from app.schemas.ollama import OllamaGenerateResponse
from app.schemas.optimize import OptimizeRequest
from app.services.optimize_stream import (
    PromptOptimizeStreamService,
)


class FakeOllamClient:

    async def generate_stream(
            self,
            prompt: str,
            system_prompt: str | None = None,
    ):
        yield OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="优",
            done=False,
        )

        yield OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="化",
            done=False,
        )

        yield OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="",
            done=True,
        )

@pytest.mark.asyncio
async def test_optimize_stream_emits_tokens_and_done():

    service = PromptOptimizeStreamService(
        ollama_client=FakeOllamClient(),
    )

    request = OptimizeRequest(
        original_prompt="hello",
        analysis_result="analysis"
    )

    events = []

    async for event in service.optimize(request):
        events.append(event)

    assert events[0].type == "token"
    assert events[0].content == "优"

    assert events[1].content == "化"

    assert events[-1].type == "done"
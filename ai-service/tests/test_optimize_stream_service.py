import pytest
from prometheus_client import REGISTRY

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


class FakeMeasuredOllamaClient:
    async def generate_stream(self, prompt: str, system_prompt: str | None = None):
        yield OllamaGenerateResponse(
            model="qwen3.5:9b", response="optimized", done=False
        )
        yield OllamaGenerateResponse(
            model="qwen3.5:9b",
            response="",
            done=True,
            prompt_eval_count=23,
            eval_count=6,
        )


@pytest.mark.asyncio
async def test_optimize_stream_records_success_and_token_metrics():
    service = PromptOptimizeStreamService(ollama_client=FakeMeasuredOllamaClient())
    status_labels = {"operation": "optimize", "status": "success"}
    operation_labels = {"operation": "optimize"}
    request_before = REGISTRY.get_sample_value("ai_request_total", status_labels) or 0
    input_before = REGISTRY.get_sample_value("ai_input_tokens_total", operation_labels) or 0
    output_before = REGISTRY.get_sample_value("ai_output_tokens_total", operation_labels) or 0

    events = [
        event
        async for event in service.optimize(
            OptimizeRequest(original_prompt="hello", analysis_result="analysis")
        )
    ]

    assert events[-1].type == "done"
    assert REGISTRY.get_sample_value("ai_request_total", status_labels) - request_before == 1
    assert REGISTRY.get_sample_value("ai_input_tokens_total", operation_labels) - input_before == 23
    assert REGISTRY.get_sample_value("ai_output_tokens_total", operation_labels) - output_before == 6

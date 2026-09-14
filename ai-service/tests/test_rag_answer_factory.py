from contextlib import AsyncExitStack

import pytest

from app.core.config import Settings
from app.rag.generation import factory
from app.rag.generation.answer_generator import LangChainAnswerGenerator
from app.rag.generation.factory import build_grounded_answer_service
from app.rag.generation.grounded_answer_service import GroundedAnswerService


def test_build_grounded_answer_service_uses_rag_settings() -> None:
    retrieval_service = object()
    settings = Settings(
        rag_chat_model="qwen3:14b",
        ollama_base_url="http://ollama.internal:11434",
        rag_retrieve_top_k=30,
        rag_final_top_k=7,
        rag_max_context_chars=16000,
        rag_min_rerank_score=0.65,
    )

    service = build_grounded_answer_service(retrieval_service, settings)

    assert isinstance(service, GroundedAnswerService)
    assert service.retriever.retrieval_service is retrieval_service
    assert service.retriever.retrieve_top_k == 30
    assert service.retriever.final_top_k == 7
    assert service.context_builder.max_chars == 16000
    assert service.min_rerank_score == 0.65
    assert service.generator.llm.model == "qwen3:14b"
    assert service.generator.llm.base_url == "http://ollama.internal:11434"
    assert service.generator.llm.temperature == 0


@pytest.mark.anyio
async def test_factory_registered_llm_cleanup_survives_partial_construction_failure(
    monkeypatch,
) -> None:
    class FakeClient:
        def __init__(self):
            self.closed = False

        def close(self):
            self.closed = True

    class FakeAsyncClient:
        def __init__(self):
            self.closed = False

        async def close(self):
            self.closed = True

    class FakeLlm:
        def __init__(self):
            self._client = FakeClient()
            self._async_client = FakeAsyncClient()

    llm = FakeLlm()
    monkeypatch.setattr(factory, "create_chat_model", lambda **kwargs: llm)
    monkeypatch.setattr(
        factory,
        "LangChainAnswerGenerator",
        lambda **kwargs: (_ for _ in ()).throw(RuntimeError("chain failed")),
    )

    with pytest.raises(RuntimeError, match="chain failed"):
        async with AsyncExitStack() as stack:
            factory.build_grounded_answer_service(
                retrieval_service=object(),
                settings=Settings(),
                exit_stack=stack,
            )

    assert llm._client.closed
    assert llm._async_client.closed


@pytest.mark.anyio
async def test_answer_generator_closes_sync_client_when_async_close_fails() -> None:
    class FakeClient:
        def __init__(self):
            self.closed = False

        def close(self):
            self.closed = True

    class BrokenAsyncClient:
        async def close(self):
            raise RuntimeError("async close failed")

    generator = object.__new__(LangChainAnswerGenerator)
    generator.llm = type(
        "FakeLlm",
        (),
        {"_client": FakeClient(), "_async_client": BrokenAsyncClient()},
    )()

    with pytest.raises(RuntimeError, match="async close failed"):
        await generator.aclose()

    assert generator.llm._client.closed

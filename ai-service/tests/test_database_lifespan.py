from fastapi import FastAPI
import pytest

from app.core.lifespan import lifespan
from app.rag.reranker import BgeReranker


@pytest.mark.anyio
async def test_lifespan_exposes_an_async_session_factory_on_app_state() -> None:
    app = FastAPI()

    async with lifespan(app):
        assert app.state.session_factory.kw["expire_on_commit"] is False
        assert str(app.state.db_engine.url).startswith("postgresql+asyncpg://")
        assert isinstance(app.state.rag_reranker, BgeReranker)
        assert app.state.rag_reranker._model is None

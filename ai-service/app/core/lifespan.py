from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI

from app.core.config import get_settings
from app.database import create_async_engine, create_session_factory
from app.rag.reranker import BgeReranker

# 生命周期管理器
@asynccontextmanager
async def lifespan(app: FastAPI):
    setting = get_settings()

    timeout = httpx.Timeout(
        timeout=setting.ollama_read_timeout,
        connect=setting.ollama_connect_timeout
    )
    # app.state 是 FastAPI 的状态管理器，存活于应用的生命周期，所有的路由都能访问
    app.state.http_client = httpx.AsyncClient(
        base_url=setting.ollama_base_url,
        timeout=timeout
    )
    app.state.db_engine = create_async_engine(setting.rag_database_url)
    app.state.session_factory = create_session_factory(app.state.db_engine)
    app.state.rag_reranker = BgeReranker(setting.rag_reranker_model)

    try:
        # 等待请求
        yield
    finally:
        await app.state.http_client.aclose()
        await app.state.db_engine.dispose()

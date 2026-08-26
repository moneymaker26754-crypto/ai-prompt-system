from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI

from app.core.config import get_settings

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

    try:
        # 等待请求
        yield
    finally:
        await app.state.http_client.aclose()
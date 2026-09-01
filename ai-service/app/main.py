from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.analyze import router as analyze_router
from app.api.optimize import router as optimize_router
from app.api.review import router as review_router
from app.api.metrics import router as metrics_router
from app.core.config import get_settings
from app.core.error_handlers import register_exception_handlers
from app.core.lifespan import lifespan
from app.core.middleware import request_id_middleware
from app.core.telemetry import configure_logging, setup_telemetry
from app.rag.router import router as rag_router

settings = get_settings()
configure_logging()


app = FastAPI(
    title = settings.service_name,
    version = settings.version,
    lifespan=lifespan
)


register_exception_handlers(app)

app.middleware("http")(request_id_middleware)

setup_telemetry(app)

app.include_router(health_router, prefix=settings.api_prefix)
app.include_router(analyze_router, prefix=settings.api_prefix)
app.include_router(optimize_router, prefix=settings.api_prefix)
app.include_router(review_router, prefix=settings.api_prefix)
app.include_router(metrics_router, prefix=settings.api_prefix)
app.include_router(rag_router)


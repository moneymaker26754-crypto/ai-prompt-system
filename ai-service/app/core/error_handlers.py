from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.exceptions import (
    AiServiceError,
    InvalidModelOutputError,
    ModelTimeoutError,
    ModelUnavailableError,
    ModelUpstreamError,
)
from app.schemas.error import ErrorResponse


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AiServiceError)
    async def handle_ai_service_error(
            request: Request,
            exc: AiServiceError,
    ) -> JSONResponse:

        if isinstance(exc, ModelTimeoutError):
            status_code = 504
        elif isinstance(exc, ModelUnavailableError):
            status_code = 503
        elif isinstance(
                exc,
                (ModelUpstreamError, InvalidModelOutputError),
        ):
            status_code = 502
        else:
            status_code = 500

        body = ErrorResponse(
            code=exc.code,
            message=exc.message,
        )

        return JSONResponse(
            status_code=status_code,
            content=body.model_dump(),
        )
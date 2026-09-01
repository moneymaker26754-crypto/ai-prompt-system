import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.exceptions import (
    AiServiceError,
    InvalidModelOutputError,
    ModelTimeoutError,
    ModelUnavailableError,
    ModelUpstreamError,
)
from app.core.request_context import get_request_id
from app.schemas.error import ErrorResponse

logger = logging.getLogger(__name__)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AiServiceError)
    async def handle_ai_service_error(
            request: Request,
            exc: AiServiceError,
    ) -> JSONResponse:
        request_id = get_request_id()

        if isinstance(exc, ModelTimeoutError):
            status_code = 504
            error_type = "timeout"
        elif isinstance(exc, ModelUnavailableError):
            status_code = 503
            error_type = "unavailable"
        elif isinstance(exc, InvalidModelOutputError):
            status_code = 502
            error_type = "invalid_output"
        elif isinstance(exc, ModelUpstreamError):
            status_code = 502
            error_type = "upstream_error"
        else:
            status_code = 500
            error_type = "internal_error"

        logger.error(
            "ai service error handled",
            extra={
                "requestId": request_id,
                "errorCode": exc.code,
                "errorType": error_type,
                "status": "error",
            },
        )

        body = ErrorResponse(
            code=exc.code,
            message=exc.message,
        )

        return JSONResponse(
            status_code=status_code,
            content=body.model_dump(),
        )

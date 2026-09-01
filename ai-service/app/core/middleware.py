import uuid

from fastapi import Request

from app.core.request_context import (
    request_id_var
)

REQUEST_ID_HEADER = "X-Request-ID"


async def request_id_middleware(request: Request, call_next):
    request_id = (request.headers.get(REQUEST_ID_HEADER) or str(uuid.uuid4()))

    token = request_id_var.set(request_id)

    try:
        response = await call_next(request)

        response.headers[REQUEST_ID_HEADER] = request_id

        return response
    finally:
        request_id_var.reset(token)
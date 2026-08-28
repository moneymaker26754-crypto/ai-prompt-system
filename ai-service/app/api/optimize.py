import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.api.dependencies import (
    get_prompt_optimize_service,
    get_prompt_optimize_stream_service,
)
from app.schemas.optimize import (
    OptimizeRequest,
    OptimizeResponse,
)
from app.services.optimize import (
    PromptOptimizeService,
)
from app.services.optimize_stream import (
    PromptOptimizeStreamService,
)


router = APIRouter(
    prefix="/prompts",
    tags=["prompts"],
)


@router.post(
    "/optimize",
    response_model=OptimizeResponse,
)
async def optimize_prompt(
    request: OptimizeRequest,
    service: PromptOptimizeService = Depends(
            get_prompt_optimize_service
    ),
) -> OptimizeResponse:
    return await service.optimize(request)


@router.post(
    "/optimize/stream",
    response_class=StreamingResponse,
)
async def optimize_stream(
    request: OptimizeRequest,
    service: PromptOptimizeStreamService = Depends(
            get_prompt_optimize_stream_service
    ),
):
    async def generate() -> AsyncIterator[str]:

        async for event in service.optimize(request):
            yield (
                event.model_dump_json() + "\n"
            )

    return StreamingResponse(
        generate(),
        media_type="application/x-ndjson",
    )
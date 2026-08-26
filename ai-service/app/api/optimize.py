from fastapi import APIRouter, Depends

from app.api.dependencies import (
    get_prompt_optimize_service,
)
from app.schemas.optimize import (
    OptimizeRequest,
    OptimizeResponse,
)
from app.services.optimize import (
    PromptOptimizeService,
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
        )
) -> OptimizeResponse:
    return await service.optimize(request)
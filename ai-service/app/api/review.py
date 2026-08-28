from fastapi import APIRouter, Depends

from app.api.dependencies import (
    get_prompt_review_service,
)
from app.schemas.review import (
    ReviewRequest,
    ReviewResponse,
)
from app.services.review import (
    PromptReviewService,
)


router = APIRouter(prefix="/prompts", tags=["prompts"])


@router.post("/review", response_model=ReviewResponse)
async def review(request: ReviewRequest, service: PromptReviewService = Depends(get_prompt_review_service)) -> ReviewResponse:
    return await service.review(request)
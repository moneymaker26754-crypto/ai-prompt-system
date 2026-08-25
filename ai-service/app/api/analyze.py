from fastapi import APIRouter, Depends

from app.api.dependencies import (
    get_prompt_analyze_service,
)
from app.schemas.analyze import (
    AnalyzeRequest,
    AnalyzeResponse,
)
from app.services.analyze import PromptAnalyzeService


router = APIRouter(
    prefix="/prompts",
    tags=["prompts"],
)


@router.post(
    "/analyze",
    response_model=AnalyzeResponse,
)
async def analyze_prompt(
        request: AnalyzeRequest,
        service: PromptAnalyzeService = Depends(
            get_prompt_analyze_service
        ),
) -> AnalyzeResponse:
    return await service.analyze(request)
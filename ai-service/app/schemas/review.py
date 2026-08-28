from enum import StrEnum

from pydantic import (BaseModel, Field, field_validator,)


class RiskLevel(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class ReviewRequest(BaseModel):
    original_prompt: str = Field(min_length=1, max_length=5000,)

    optimized_prompt: str = Field(min_length=1, max_length=10000,)

    @field_validator("original_prompt", "optimized_prompt",)
    @classmethod
    def validate_not_blank(cls, value: str,) -> str:

        value = value.strip()

        if not value:
            raise ValueError("prompt must not be blank")

        return value


class ReviewResponse(BaseModel):
    score: int = Field(ge=0, le=100,)

    risk_level: RiskLevel

    changed_intent: bool

    review_comment: str = Field(min_length=1, max_length=2000,)

    model: str
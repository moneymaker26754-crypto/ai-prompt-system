from pydantic import BaseModel, Field, field_validator


class AnalyzeRequest(BaseModel):
    original_prompt: str = Field(
        min_length=1,
        max_length=5000,
    )

    system_prompt: str | None = None

    @field_validator("original_prompt")
    @classmethod
    def validate_original_prompt(cls, value: str) -> str:
        value = value.strip()

        if not value:
            raise ValueError("original_prompt cannot be empty")

        return value


class AnalyzeResponse(BaseModel):
    analysis: str
    model: str
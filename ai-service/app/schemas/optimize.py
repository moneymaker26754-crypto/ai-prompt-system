from pydantic import BaseModel, Field, field_validator


class OptimizeRequest(BaseModel):
    original_prompt: str = Field(min_length=1, max_length=5000,)

    analysis_result: str

    instruction: str | None = Field(default=None, max_length=1000,)

    target: str | None = Field(default=None, max_length=500,)

    output_format: str | None = Field(default=None, max_length=500,)

    system_prompt: str | None = None

    @field_validator("original_prompt")
    @classmethod
    def validate_original_prompt(cls, value: str,) -> str:
        value = value.strip()

        if not value:
            raise ValueError("Original prompt cannot be empty")

        return value


class OptimizeResponse(BaseModel):
    optimized_prompt: str
    model: str

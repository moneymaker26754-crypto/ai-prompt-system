from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
)


class GeneratedCitation(BaseModel):

    source_id: str = Field(
        description=(
            "Source ID supporting the answer, "
            "for example S1"
        )
    )


class GroundedAnswer(BaseModel):

    answerable: bool = Field(
        description=(
            "Whether the retrieved context "
            "contains enough information "
            "to answer the question."
        )
    )

    answer: str = Field(
        description=(
            "Answer grounded only in "
            "retrieved context."
        )
    )

    citations: list[str] = Field(
        default_factory=list,
        description=(
            "Source IDs supporting the answer, "
            "for example ['S1', 'S3']."
        ),
    )

    reason: str | None = Field(
        default=None,
        description=(
            "Reason the question cannot be "
            "answered when answerable is false."
        ),
    )


class CitationResponse(BaseModel):

    source_id: str

    document_id: str | None
    chunk_id: str | None

    file_name: str | None

    chunk_index: int | None

    preview: str


class RagAnswerResponse(BaseModel):

    answerable: bool

    answer: str

    citations: list[CitationResponse]

    reason: str | None = None


class RagAnswerRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    knowledge_base_id: str = Field(
        min_length=1,
        max_length=64,
    )

    question: str = Field(
        min_length=1,
        max_length=2000,
    )

    @field_validator("knowledge_base_id", "question", mode="before")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        return value.strip() if isinstance(value, str) else value

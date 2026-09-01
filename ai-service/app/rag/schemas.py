from pathlib import PurePosixPath

from pydantic import BaseModel, ConfigDict, Field, field_validator


class RagIngestRequest(BaseModel):
    path: str
    knowledge_base_id: str = Field(min_length=1, max_length=64)

    @field_validator("knowledge_base_id", mode="before")
    @classmethod
    def strip_knowledge_base_id(cls, value: str) -> str:
        return value.strip() if isinstance(value, str) else value


class RagSearchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    knowledge_base_id: str = Field(min_length=1, max_length=64)
    query: str = Field(min_length=1, max_length=2000)
    top_k: int = Field(default=10, ge=1, le=20)
    rerank: bool = False

    @field_validator("knowledge_base_id", "query", mode="before")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        return value.strip() if isinstance(value, str) else value


class RagSearchResult(BaseModel):
    rank: int

    chunk_id: str
    document_id: str

    source: str | None
    file_name: str | None
    chunk_index: int | None
    char_start: int | None
    char_end: int | None

    vector_score: float
    rerank_score: float | None = None

    content: str


class RagSearchResponse(BaseModel):
    query: str
    results: list[RagSearchResult]


class RelevantChunk(BaseModel):
    source: str
    chunk_index: int = Field(ge=0)

    @field_validator("source")
    @classmethod
    def validate_source(cls, value: str) -> str:
        source = value.strip()
        if not source or "\\" in source or PurePosixPath(source).is_absolute():
            raise ValueError("source must be a non-empty relative POSIX path")
        return source


class EvaluationCase(BaseModel):
    knowledge_base_id: str = Field(min_length=1, max_length=64)
    query: str = Field(min_length=1, max_length=2000)
    relevant_chunks: list[RelevantChunk] = Field(min_length=1)

    @field_validator("knowledge_base_id", "query", mode="before")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        return value.strip() if isinstance(value, str) else value


class RagEvaluateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    dataset: str = Field(min_length=1, max_length=500)
    top_k: int = Field(default=5, ge=1, le=20)
    rerank: bool = False
    include_failed_queries: bool = False

    @field_validator("dataset", mode="before")
    @classmethod
    def strip_dataset(cls, value: str) -> str:
        return value.strip() if isinstance(value, str) else value


class RagCompareRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    dataset: str = Field(min_length=1, max_length=500)

    @field_validator("dataset", mode="before")
    @classmethod
    def strip_dataset(cls, value: str) -> str:
        return value.strip() if isinstance(value, str) else value


class RetrievalBenchmarkMetrics(BaseModel):
    recall_at_5: float
    recall_at_10: float
    mrr_at_5: float
    mrr_at_10: float
    p95_latency_ms: float


class RagComparisonResponse(BaseModel):
    queries: int
    exact: RetrievalBenchmarkMetrics
    hnsw: RetrievalBenchmarkMetrics


class EvaluationRetrievedChunk(BaseModel):
    rank: int
    source: str | None
    chunk_index: int
    vector_score: float
    rerank_score: float | None = None


class FailedEvaluationQuery(BaseModel):
    query: str
    expected: list[RelevantChunk]
    retrieved: list[EvaluationRetrievedChunk]

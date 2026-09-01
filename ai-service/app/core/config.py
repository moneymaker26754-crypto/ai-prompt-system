from functools import lru_cache
from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service_name: str = "ai-prompt-service"
    version: str = "0.1.0"
    api_prefix: str = "/v1"
    environment: str = "dev"

    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen3.5:9b"

    ollama_connect_timeout: float = 5.0
    ollama_read_timeout: float = 120.0
    internal_api_key: str | None = Field(
        default=None,
        validation_alias=AliasChoices("AI_INTERNAL_API_KEY", "INTERNAL_API_KEY"),
    )

    rag_database_url: str = Field(
        default="postgresql+asyncpg://rag:rag_password@localhost:5433/rag",
        validation_alias=AliasChoices("AI_RAG_DATABASE_URL", "RAG_DATABASE_URL"),
    )
    rag_embedding_model: str = Field(
        default="qwen3-embedding:0.6b",
        validation_alias=AliasChoices("AI_RAG_EMBEDDING_MODEL", "RAG_EMBEDDING_MODEL"),
    )
    rag_reranker_model: str = Field(
        default="BAAI/bge-reranker-v2-m3",
        validation_alias=AliasChoices("AI_RAG_RERANKER_MODEL", "RAG_RERANKER_MODEL"),
    )
    rag_eval_dataset_dir: Path = Field(
        default=Path("rag-eval"),
        validation_alias=AliasChoices(
            "AI_RAG_EVAL_DATASET_DIR",
            "RAG_EVAL_DATASET_DIR",
        ),
    )
    rag_chunk_size: int = Field(
        default=800,
        validation_alias=AliasChoices("AI_RAG_CHUNK_SIZE", "RAG_CHUNK_SIZE"),
    )
    rag_chunk_overlap: int = Field(
        default=120,
        validation_alias=AliasChoices("AI_RAG_CHUNK_OVERLAP", "RAG_CHUNK_OVERLAP"),
    )

    model_config = SettingsConfigDict(
        env_prefix="AI_",
        env_file=".env",
        extra="ignore",
    )

# 重复创建配置对象、重复读取配置
@lru_cache
def get_settings() -> Settings:
    return Settings()

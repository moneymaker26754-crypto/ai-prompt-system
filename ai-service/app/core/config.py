from functools import lru_cache

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

    model_config = SettingsConfigDict(
        env_prefix="AI_",
        env_file=".env",
        extra="ignore",
    )

# 重复创建配置对象、重复读取配置
@lru_cache
def get_settings() -> Settings:
    return Settings()
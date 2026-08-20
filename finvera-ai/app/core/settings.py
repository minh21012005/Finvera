from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Service info
    app_name: str = "finvera-ai"
    environment: str = "development"

    # Security (X-Internal-Api-Key)
    internal_api_key: str = "dev-internal-key-change-in-prod"

    # Gemini LLM & Embedding (ADR-0002, ADR-0008, research R-005)
    gemini_api_key: Optional[str] = None
    gemini_generation_model: str = "gemini-2.5-flash"
    gemini_embedding_model: str = "text-embedding-004"
    embedding_dimension: int = 768
    embedding_version: str = "gemini-embedding-v1"

    # Qdrant Vector DB (research R-006)
    qdrant_url: Optional[str] = None
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333
    qdrant_collection_name: str = "research_chunks_v1"

    # Ingestion Callback (finvera-be URL)
    backend_internal_api_url: str = "http://127.0.0.1:8080/internal/v1"

    # Feature 007 - AI Analyst (research R-005, R-010)
    analyst_max_tool_calls: int = 10
    analyst_tool_call_timeout_seconds: float = 10.0
    analyst_ask_timeout_seconds: float = 30.0


settings = Settings()


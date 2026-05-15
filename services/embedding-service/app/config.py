
import os
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    kafka_bootstrap_servers: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
    kafka_topic_raw: str = os.getenv("KAFKA_TOPIC_RAW", "contextengine.knowledge.raw")
    kafka_topic_processed: str = os.getenv("KAFKA_TOPIC_PROCESSED", "contextengine.knowledge.processed")
    kafka_topic_errors: str = os.getenv("KAFKA_TOPIC_ERRORS", "contextengine.knowledge.errors")
    kafka_group_id: str = os.getenv("KAFKA_GROUP_ID", "embedding-service")

    qdrant_host: str = os.getenv("QDRANT_HOST", "localhost")
    qdrant_port: int = int(os.getenv("QDRANT_PORT", "6333"))
    vector_dimensions: int = 768  # nomic-embed-text output size

    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    ollama_embedding_model: str = os.getenv("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text")

    chunk_size_tokens: int = 512
    chunk_overlap_tokens: int = 50

    # PII detection — requires presidio-analyzer and presidio-anonymizer
    pii_detection_enabled: bool = os.getenv("PII_DETECTION_ENABLED", "false").lower() == "true"

    # Kafka poll loop interval in milliseconds
    kafka_poll_timeout_ms: int = 1000

    class Config:
        env_prefix = ""


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings

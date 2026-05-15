
import logging

import httpx
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
    before_sleep_log,
)

from app.config import Settings

logger = logging.getLogger(__name__)


class EmbeddingError(Exception):
    """Raised when embedding generation fails after all retries."""


@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=15),
    retry=retry_if_exception_type((httpx.RequestError, httpx.HTTPStatusError)),
    before_sleep=before_sleep_log(logger, logging.WARNING),
    reraise=True,
)
def _embed_with_retry(text: str, settings: Settings) -> list[float]:
    """
    Call Ollama's /api/embeddings endpoint and return the embedding vector.
    Retried automatically on transient network errors.
    """
    with httpx.Client(timeout=60) as client:
        response = client.post(
            f"{settings.ollama_base_url}/api/embeddings",
            json={"model": settings.ollama_embedding_model, "prompt": text},
        )
        response.raise_for_status()
        data = response.json()
        embedding = data.get("embedding")
        if not embedding:
            raise EmbeddingError(
                f"Ollama returned no embedding for model {settings.ollama_embedding_model}"
            )
        return embedding


def embed_texts(texts: list[str], settings: Settings) -> list[list[float]]:
    """
    Embed a list of texts. Each text is embedded independently.
    Raises EmbeddingError if any embedding fails after retries.
    """
    embeddings: list[list[float]] = []

    for i, text in enumerate(texts):
        try:
            vector = _embed_with_retry(text, settings)
            embeddings.append(vector)
            logger.debug("Embedded chunk %d/%d (%d chars)", i + 1, len(texts), len(text))
        except Exception as exc:
            raise EmbeddingError(
                f"Failed to embed chunk {i + 1}/{len(texts)} after retries: {exc}"
            ) from exc

    return embeddings

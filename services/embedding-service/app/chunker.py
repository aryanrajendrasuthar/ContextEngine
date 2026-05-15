
import logging
from typing import Optional

import tiktoken

from app.models import KnowledgeEvent, TextChunk

logger = logging.getLogger(__name__)

# cl100k_base is a close approximation for nomic-embed-text's tokenizer.
# Exact count is not required — chunking by token count is a best-effort boundary.
_ENC = tiktoken.get_encoding("cl100k_base")


def chunk_event(event: KnowledgeEvent, max_tokens: int = 512, overlap: int = 50) -> list[TextChunk]:
    """
    Split an event's content into overlapping text chunks that fit within max_tokens.

    Overlap preserves context at chunk boundaries. A sentence split in half by a
    chunk boundary will appear in full in the next chunk, so the embedding for that
    chunk captures the complete thought.

    Returns a single chunk if the content fits within max_tokens.
    """
    raw_chunks = _split_text(event.content, max_tokens, overlap)
    total = len(raw_chunks)

    return [
        TextChunk(
            source_id=event.sourceId,
            organization_id=event.organizationId,
            source_type=event.sourceType,
            chunk_index=i,
            total_chunks=total,
            text=text,
            author_id=event.authorId,
            author_name=event.authorName,
            timestamp=event.timestamp,
            url=event.url,
            metadata=event.metadata,
        )
        for i, text in enumerate(raw_chunks)
    ]


def _split_text(text: str, max_tokens: int, overlap: int) -> list[str]:
    tokens = _ENC.encode(text)

    if len(tokens) <= max_tokens:
        return [text]

    chunks: list[str] = []
    start = 0

    while start < len(tokens):
        end = min(start + max_tokens, len(tokens))
        chunk_tokens = tokens[start:end]

        try:
            chunk_text = _ENC.decode(chunk_tokens)
        except Exception:
            # If a token boundary falls mid-character (rare with BPE), decode what we can
            chunk_text = _ENC.decode(chunk_tokens[:-1]) if len(chunk_tokens) > 1 else ""

        if chunk_text:
            chunks.append(chunk_text)

        if end == len(tokens):
            break

        start += max_tokens - overlap

    logger.debug("Chunked %d tokens into %d chunks (max=%d, overlap=%d)",
                 len(tokens), len(chunks), max_tokens, overlap)
    return chunks


import logging
import re
import uuid

from qdrant_client import QdrantClient
from qdrant_client.http.models import (
    Distance,
    PointStruct,
    VectorParams,
)

from app.config import Settings
from app.models import TextChunk

logger = logging.getLogger(__name__)

_client: QdrantClient | None = None


def get_client(settings: Settings) -> QdrantClient:
    global _client
    if _client is None:
        _client = QdrantClient(host=settings.qdrant_host, port=settings.qdrant_port)
        logger.info("Qdrant client initialized: %s:%d", settings.qdrant_host, settings.qdrant_port)
    return _client


def collection_name(organization_id: str) -> str:
    """
    Derive a Qdrant-safe collection name from an organization ID.
    Qdrant collection names must be alphanumeric with underscores.
    """
    sanitized = re.sub(r"[^a-zA-Z0-9]", "_", organization_id)
    return f"org_{sanitized}"


def ensure_collection(organization_id: str, settings: Settings) -> None:
    """
    Create the Qdrant collection for an organization if it does not already exist.
    Idempotent — safe to call on every event.
    """
    client = get_client(settings)
    name = collection_name(organization_id)

    existing = {c.name for c in client.get_collections().collections}
    if name not in existing:
        client.create_collection(
            collection_name=name,
            vectors_config=VectorParams(
                size=settings.vector_dimensions,
                distance=Distance.COSINE,
            ),
        )
        logger.info("Created Qdrant collection: %s", name)


def upsert_chunks(
    chunks: list[TextChunk],
    embeddings: list[list[float]],
    settings: Settings,
) -> list[str]:
    """
    Upsert chunk embeddings into the organization's Qdrant collection.
    Point IDs are deterministic (UUID5) so re-processing the same event is idempotent.
    Returns the list of point IDs upserted.
    """
    if not chunks:
        return []

    organization_id = chunks[0].organization_id
    ensure_collection(organization_id, settings)

    client = get_client(settings)
    name = collection_name(organization_id)

    points: list[PointStruct] = []
    point_ids: list[str] = []

    for chunk, vector in zip(chunks, embeddings):
        # Deterministic ID: same sourceId+chunkIndex always produces the same UUID
        point_id = str(
            uuid.uuid5(uuid.NAMESPACE_URL, f"{chunk.source_id}:{chunk.chunk_index}")
        )
        point_ids.append(point_id)

        payload = {
            "source_id": chunk.source_id,
            "source_type": chunk.source_type,
            "organization_id": chunk.organization_id,
            "chunk_index": chunk.chunk_index,
            "total_chunks": chunk.total_chunks,
            "content": chunk.text,
            "author_id": chunk.author_id,
            "author_name": chunk.author_name,
            "url": chunk.url,
            "metadata": chunk.metadata or {},
        }
        if chunk.timestamp:
            payload["timestamp"] = chunk.timestamp.isoformat()

        points.append(PointStruct(id=point_id, vector=vector, payload=payload))

    client.upsert(collection_name=name, points=points)
    logger.info(
        "Upserted %d chunks into Qdrant collection %s (source_id=%s)",
        len(points), name, chunks[0].source_id,
    )
    return point_ids

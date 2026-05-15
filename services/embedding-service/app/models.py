
from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class KnowledgeEvent(BaseModel):
    """
    Mirrors the KnowledgeEvent record published to Kafka by the ingestion service.
    Field names match the JSON keys produced by Spring's JsonSerializer.
    """
    sourceId: str
    sourceType: str
    content: str
    organizationId: str
    authorId: Optional[str] = None
    authorName: Optional[str] = None
    timestamp: Optional[datetime] = None
    url: Optional[str] = None
    metadata: Optional[dict] = None


class TextChunk(BaseModel):
    """A single chunk produced by splitting an event's content."""
    source_id: str
    organization_id: str
    source_type: str
    chunk_index: int
    total_chunks: int
    text: str
    author_id: Optional[str] = None
    author_name: Optional[str] = None
    timestamp: Optional[datetime] = None
    url: Optional[str] = None
    metadata: Optional[dict] = None


class ProcessedEvent(BaseModel):
    """
    Published to contextengine.knowledge.processed after successful embedding.
    The knowledge-graph-service reads this topic to build Neo4j nodes and relationships.
    """
    sourceId: str
    sourceType: str
    content: str
    organizationId: str
    authorId: Optional[str] = None
    authorName: Optional[str] = None
    timestamp: Optional[datetime] = None
    url: Optional[str] = None
    metadata: Optional[dict] = None
    qdrant_point_ids: list[str]
    chunk_count: int
    processing_timestamp: datetime


class FailedEvent(BaseModel):
    """Published to contextengine.knowledge.errors (dead letter queue)."""
    source_id: str
    organization_id: str
    source_type: str
    failure_reason: str
    failure_timestamp: datetime
    original_content_preview: str  # first 200 chars for debugging

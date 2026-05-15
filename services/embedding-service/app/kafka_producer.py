
import json
import logging
from datetime import datetime, timezone

from kafka import KafkaProducer

from app.config import Settings
from app.models import FailedEvent, KnowledgeEvent, ProcessedEvent

logger = logging.getLogger(__name__)

_producer: KafkaProducer | None = None


def get_producer(settings: Settings) -> KafkaProducer:
    global _producer
    if _producer is None:
        _producer = KafkaProducer(
            bootstrap_servers=settings.kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",
            retries=3,
        )
        logger.info("Kafka producer initialized: %s", settings.kafka_bootstrap_servers)
    return _producer


def publish_processed(event: KnowledgeEvent, point_ids: list[str], settings: Settings) -> None:
    """Publish a successfully embedded event to the processed topic."""
    processed = ProcessedEvent(
        sourceId=event.sourceId,
        sourceType=event.sourceType,
        content=event.content,
        organizationId=event.organizationId,
        authorId=event.authorId,
        authorName=event.authorName,
        timestamp=event.timestamp,
        url=event.url,
        metadata=event.metadata,
        qdrant_point_ids=point_ids,
        chunk_count=len(point_ids),
        processing_timestamp=datetime.now(tz=timezone.utc),
    )
    producer = get_producer(settings)
    producer.send(
        settings.kafka_topic_processed,
        key=event.organizationId,
        value=processed.model_dump(),
    )
    logger.debug(
        "Published processed event: source_id=%s, chunks=%d",
        event.sourceId, len(point_ids),
    )


def publish_failed(event: KnowledgeEvent, reason: str, settings: Settings) -> None:
    """Publish a failed event to the dead letter queue."""
    failed = FailedEvent(
        source_id=event.sourceId,
        organization_id=event.organizationId,
        source_type=event.sourceType,
        failure_reason=reason,
        failure_timestamp=datetime.now(tz=timezone.utc),
        original_content_preview=event.content[:200],
    )
    producer = get_producer(settings)
    producer.send(
        settings.kafka_topic_errors,
        key=event.organizationId,
        value=failed.model_dump(),
    )
    logger.warning(
        "Published failed event to DLQ: source_id=%s, reason=%s",
        event.sourceId, reason,
    )

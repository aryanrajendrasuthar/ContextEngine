
import json
import logging
import threading
import time

from kafka import KafkaConsumer
from kafka.errors import KafkaError
from pydantic import ValidationError

from app.chunker import chunk_event
from app.config import Settings, get_settings
from app.embedder import EmbeddingError, embed_texts
from app.kafka_producer import publish_failed, publish_processed
from app.models import KnowledgeEvent
from app.vector_store import upsert_chunks

logger = logging.getLogger(__name__)

# Set by main.py on startup to signal the consumer loop to stop
_stop_event = threading.Event()


def stop_consumer() -> None:
    """Signal the consumer thread to shut down after the current poll cycle."""
    _stop_event.set()


def _process_event(event: KnowledgeEvent, settings: Settings) -> None:
    """
    Full processing pipeline for a single knowledge event:
      1. Chunk the content into 512-token segments with 50-token overlap
      2. Generate embeddings for each chunk via Ollama
      3. Upsert chunk vectors into Qdrant (one collection per organization)
      4. Publish processed event to the processed topic

    On any failure, the event is sent to the dead letter queue and the offset
    is committed so the consumer does not loop on a permanently bad message.
    """
    logger.info(
        "Processing event: source_id=%s, org=%s, source_type=%s",
        event.sourceId, event.organizationId, event.sourceType,
    )

    chunks = chunk_event(event, settings.chunk_size_tokens, settings.chunk_overlap_tokens)
    logger.debug("Produced %d chunks for source_id=%s", len(chunks), event.sourceId)

    texts = [c.text for c in chunks]
    embeddings = embed_texts(texts, settings)

    point_ids = upsert_chunks(chunks, embeddings, settings)

    publish_processed(event, point_ids, settings)
    logger.info(
        "Completed event: source_id=%s, chunks=%d, points=%d",
        event.sourceId, len(chunks), len(point_ids),
    )


def _deserialize_message(raw_value: bytes) -> KnowledgeEvent | None:
    try:
        data = json.loads(raw_value.decode("utf-8"))
        return KnowledgeEvent.model_validate(data)
    except (json.JSONDecodeError, ValidationError, UnicodeDecodeError) as exc:
        logger.error("Failed to deserialize Kafka message: %s", exc)
        return None


def run_consumer() -> None:
    """
    Main Kafka consumer loop. Runs in a background thread.
    Reads from the raw knowledge topic, processes each event, and commits offsets
    after each message is either processed successfully or sent to the DLQ.
    """
    settings = get_settings()
    logger.info("Starting Kafka consumer: topic=%s, group=%s",
                settings.kafka_topic_raw, settings.kafka_group_id)

    # Wait briefly on startup to allow Kafka to become available
    time.sleep(3)

    consumer: KafkaConsumer | None = None
    while not _stop_event.is_set():
        try:
            consumer = KafkaConsumer(
                settings.kafka_topic_raw,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_group_id,
                auto_offset_reset="earliest",
                enable_auto_commit=False,
                value_deserializer=None,  # raw bytes, deserialized manually
                consumer_timeout_ms=settings.kafka_poll_timeout_ms,
            )
            logger.info("Kafka consumer connected successfully")
            break
        except KafkaError as exc:
            logger.warning("Kafka not yet available, retrying in 5s: %s", exc)
            time.sleep(5)

    if consumer is None:
        logger.error("Failed to connect to Kafka — consumer thread exiting")
        return

    try:
        for message in consumer:
            if _stop_event.is_set():
                break

            event = _deserialize_message(message.value)

            if event is None:
                # Malformed message — commit and skip (do not loop forever on bad data)
                consumer.commit()
                continue

            try:
                _process_event(event, settings)
            except EmbeddingError as exc:
                logger.error("Embedding failed for source_id=%s: %s", event.sourceId, exc)
                publish_failed(event, str(exc), settings)
            except Exception as exc:
                logger.error(
                    "Unexpected error processing source_id=%s: %s",
                    event.sourceId, exc, exc_info=True,
                )
                publish_failed(event, f"unexpected_error: {exc}", settings)

            # Commit offset regardless of success or failure.
            # Failures go to DLQ; we do not want to replay them on restart.
            consumer.commit()

    except Exception as exc:
        logger.error("Consumer loop failed: %s", exc, exc_info=True)
    finally:
        try:
            consumer.close()
        except Exception:
            pass
        logger.info("Kafka consumer shut down")

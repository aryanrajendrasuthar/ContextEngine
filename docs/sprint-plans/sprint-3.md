
# Sprint 3: Embedding Pipeline and Vector Search

**Duration:** Day 3, approximately 3 hours
**Status:** Planned

## Goal

Build the AI core — the pipeline that transforms raw knowledge into semantic embeddings and stores them in Qdrant for intelligent retrieval.

## Deliverables

- embedding-service (Python FastAPI): Kafka consumer reading from contextengine.knowledge.raw
- Chunking strategy: 512-token chunks with 50-token overlap and metadata preservation
- Embedding generation via Ollama (nomic-embed-text model) with retry logic and batch processing
- Qdrant integration: Store embeddings with full payload (sourceType, sourceId, chunkIndex, content, authorName, timestamp, url, organizationId)
- Qdrant collections: one collection per organization for tenant isolation
- Processed event publishing to contextengine.knowledge.processed with Qdrant point ID
- Dead letter queue: failed events published to contextengine.knowledge.errors with failure reason
- Embedding health endpoint: GET /health with Qdrant and Ollama connection status
- LEARNING.md Sprint 3 section (including performance fundamentals section)

## Commit Checkpoints

- CHECKPOINT 3A: Kafka consumer and chunking logic
- CHECKPOINT 3B: Ollama embedding generation and Qdrant storage
- CHECKPOINT 3C: Dead letter queue, retry logic, health endpoints

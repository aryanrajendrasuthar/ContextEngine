
# ADR-003: Event-Driven Ingestion via Kafka

**Status:** Accepted
**Date:** 2024-05-14
**Authors:** Platform Engineering Team

## Context

ContextEngine must ingest knowledge from multiple source systems — Slack, GitHub, Jira, Confluence — and process each event through multiple stages: validation, embedding generation, and knowledge graph construction. Each stage has different throughput and latency characteristics. Embedding generation via a local LLM (Ollama) is the slowest stage at roughly 200 documents per minute. Graph construction requires Neo4j writes and NLP processing. Ingestion must not be blocked or throttled by the slowest downstream stage.

The architectural question is: when a connector sends a new knowledge event to the system, should downstream processing (embedding, graph construction) happen synchronously in the same HTTP request, or should the event be buffered and processed asynchronously?

## Decision

All knowledge processing after initial validation will be event-driven, with Apache Kafka as the message broker.

## Rationale

Synchronous processing would mean the connector service's HTTP request to the ingestion service blocks until the embedding is generated and the graph node is created. An Ollama embedding request takes 50–300ms depending on document length and hardware. At 200 documents per minute throughput, any synchronous HTTP chain would create head-of-line blocking — one slow embedding request would delay all subsequent ingestion.

More critically, synchronous coupling means that if the embedding-service is down for a restart or rolling update, knowledge events are lost or the connector service must implement retry logic. This pushes retry and backpressure responsibility onto every caller, which is the wrong place for it.

Kafka decouples producers from consumers. The ingestion-service publishes validated events to `contextengine.knowledge.raw` and returns 202 Accepted to the caller immediately. The embedding-service consumes from that topic at its own pace. If the embedding-service is restarted, uncommitted Kafka offsets ensure no events are lost — they are reprocessed from the last committed offset when the service recovers.

This also enables independent scaling of each stage. If embedding throughput is the bottleneck, additional embedding-service replicas can be added — they will each consume from different Qdrant-assigned partitions, increasing parallel throughput without any changes to the ingestion-service.

The alternative considered was Redis Streams. Redis Streams provide similar publish-subscribe semantics with consumer groups. The decision against Redis Streams came down to Kafka's stronger durability guarantees (configurable replication factor and retention), its superior ecosystem for monitoring consumer lag (which is a critical operational metric for a pipeline), and its native support for partitioning by key — all events for an organization can be routed to the same partition, preserving processing order within an organization. Redis Streams would require the same Redis instance to serve both as the event bus and as the caching layer, mixing concerns.

RabbitMQ was also evaluated. RabbitMQ is a mature message broker with good Spring support. The key difference is semantic: RabbitMQ is a message queue (each message is consumed and deleted), while Kafka is a distributed log (messages are retained for a configurable duration and can be replayed). The ability to replay events is critical for ContextEngine — if the knowledge graph service has a bug that creates incorrect relationships, the fix is to correct the bug and replay the `contextengine.knowledge.processed` topic from the beginning, rebuilding the graph from the already-generated embeddings. This replay capability does not exist in RabbitMQ without significant additional infrastructure.

## Consequences

- All services that publish or consume from Kafka must handle connection failures gracefully via Resilience4j circuit breakers.
- Kafka topics are created with a minimum of 3 partitions. The partition key for all knowledge events is the organization ID.
- Kafka consumers use consumer groups, enabling multiple instances of the same service to share topic consumption without duplicate processing.
- All consumers must implement idempotent processing — if a message is redelivered (which can happen under at-least-once delivery), processing it twice must produce the same result as processing it once. For the embedding-service, this means checking whether a Qdrant point with the same source ID already exists before inserting.
- Consumer lag on `contextengine.knowledge.raw` and `contextengine.knowledge.processed` is a critical operational metric. Grafana dashboards must display this, and alerts must fire when lag exceeds 1,000 events.

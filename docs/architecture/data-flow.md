
# Data Flow: From Source Event to Queryable Knowledge

This document traces the complete lifecycle of a piece of knowledge as it enters ContextEngine, gets processed, and becomes answerable through natural language queries. The example used throughout is a Slack message, but the flow is identical for GitHub pull requests, Jira tickets, and Confluence pages.

## Stage 1: Source Event Capture

A Slack message is posted in the #architecture-decisions channel: "We decided to move the auth service off PostgreSQL sequences for user IDs to UUIDs — see the discussion in ARCH-441 and the PR linked there."

The Slack connector in the connector-service either receives this message via a Slack webhook or discovers it during a scheduled polling run. The connector normalizes the raw Slack API payload into a KnowledgeEvent:

```json
{
  "sourceId": "slack-msg-C0ABC123-1715123456.789",
  "sourceType": "SLACK",
  "content": "We decided to move the auth service off PostgreSQL sequences for user IDs to UUIDs — see the discussion in ARCH-441 and the PR linked there.",
  "authorId": "U0XYZ789",
  "authorName": "Jane Smith",
  "timestamp": "2024-05-14T10:30:00Z",
  "url": "https://yourcompany.slack.com/archives/C0ABC123/p1715123456789",
  "metadata": {
    "channel": "architecture-decisions",
    "channelId": "C0ABC123",
    "threadTs": null
  }
}
```

The connector posts this event to the ingestion-service via `POST /api/v1/events`.

## Stage 2: Ingestion and Deduplication

The ingestion-service receives the KnowledgeEvent and performs three operations before publishing it:

First, it validates the event against the schema. Missing required fields return a 400 with an RFC 7807 Problem Details error body.

Second, it computes a SHA-256 hash of the content field and checks a recent-events bloom filter in Redis. If the same content has been ingested within the past 24 hours (detected by hash collision), the event is discarded with a 200 response — idempotent behavior prevents duplicate processing when connectors retry. The hash is stored in PostgreSQL's knowledge_events table with status RECEIVED.

Third, it publishes the event to the Kafka topic `contextengine.knowledge.raw` with the organization ID as the partition key, ensuring all events from a single organization land on the same partition and are processed in order.

## Stage 3: Embedding Generation

The embedding-service consumes the event from `contextengine.knowledge.raw`. For short events like Slack messages, the content is short enough to embed as a single chunk. For longer documents — GitHub PR descriptions, Confluence pages — the service applies a sliding window chunker: 512 tokens per chunk with a 50-token overlap to preserve context at chunk boundaries.

For each chunk, the service calls the Ollama API running locally at `http://ollama:11434/api/embeddings` with the `nomic-embed-text` model. Ollama returns a 768-dimensional vector representing the semantic meaning of that chunk.

The embedding and its metadata payload are stored in Qdrant:

```json
{
  "id": "a3f1c2d4-...",
  "vector": [0.021, -0.134, 0.089, ...],
  "payload": {
    "organizationId": "org-acme-corp",
    "sourceId": "slack-msg-C0ABC123-1715123456.789",
    "sourceType": "SLACK",
    "chunkIndex": 0,
    "content": "We decided to move the auth service off PostgreSQL sequences...",
    "authorName": "Jane Smith",
    "timestamp": "2024-05-14T10:30:00Z",
    "url": "https://yourcompany.slack.com/archives/C0ABC123/p1715123456789"
  }
}
```

After successful storage, the embedding-service updates the event status in PostgreSQL to EMBEDDED and publishes to `contextengine.knowledge.processed` with the Qdrant point ID included.

## Stage 4: Knowledge Graph Construction

The knowledge-graph-service consumes from `contextengine.knowledge.processed`. It runs the event content through a spaCy NER pipeline to extract named entities: people ("Jane Smith"), organizations, and technical concepts ("auth service", "PostgreSQL", "UUIDs").

It then creates or updates Neo4j nodes and relationships:

```
(jane_smith:Person {name: "Jane Smith", authorId: "U0XYZ789"})
  -[:AUTHORED]->
(doc:Document {sourceId: "slack-msg-...", url: "...", timestamp: "..."})
  -[:MENTIONS]->
(auth_service:Concept {name: "auth service"})
  -[:REFERENCES]->
(arch441:Document {sourceId: "ARCH-441"})
```

The graph now connects Jane Smith to the auth service decision to the Jira ticket, even though they live in separate source systems.

## Stage 5: Natural Language Query

Three weeks later, a new engineer joins and asks: "Why do we use UUIDs instead of auto-increment integers for user IDs?"

The query-service receives this through `POST /api/v1/query`. It first checks the Redis cache — the hash of the question plus organization ID has no cached entry, so processing continues.

**Step 1 — Embed the question.** The question is sent to Ollama nomic-embed-text, producing a 768-dimensional query vector.

**Step 2 — Vector search.** Qdrant receives the query vector and returns the top 8 semantically similar chunks from the organization's collection. The Slack message from Jane Smith scores highly. So does a GitHub PR description that mentions the UUID migration. So does a Jira ticket ARCH-441 description.

**Step 3 — Graph context enrichment.** The knowledge-graph-service is queried: "Given these source IDs, who authored them, and what related concepts and decisions do they connect to?" The graph traversal returns that Jane Smith authored the message, that she also authored the linked PR, and that the concept "UUID" connects to three other documents.

**Step 4 — Prompt assembly.** The query-service assembles a prompt that combines the retrieved chunks with graph context:

```
You are an assistant that answers questions using only the provided company knowledge.
Answer the question below using the context provided. Cite your sources.

CONTEXT:
[Slack, #architecture-decisions, Jane Smith, 2024-05-14]:
"We decided to move the auth service off PostgreSQL sequences for user IDs to UUIDs — 
see the discussion in ARCH-441 and the PR linked there."

[Jira, ARCH-441, ...]:
"UUID vs Sequential IDs for Auth Service: Using database sequences creates coordination 
problems at scale and leaks information about record counts to clients..."

[GitHub PR #892, Jane Smith, ...]:
"Migrate user IDs to UUID v4. Sequences are predictable and expose business metrics..."

QUESTION: Why do we use UUIDs instead of auto-increment integers for user IDs?
```

**Step 5 — LLM answer generation.** The prompt is sent to Ollama llama3.1:8b. The model generates a grounded answer referencing only the provided context, with no hallucination of external knowledge.

**Step 6 — Response.** The query-service returns:

```json
{
  "answer": "The switch to UUIDs for user IDs was driven by two concerns...",
  "sources": [
    {
      "type": "SLACK",
      "content": "We decided to move the auth service off PostgreSQL sequences...",
      "authorName": "Jane Smith",
      "timestamp": "2024-05-14T10:30:00Z",
      "url": "https://yourcompany.slack.com/archives/..."
    },
    ...
  ],
  "confidence": 0.92,
  "relatedConcepts": ["auth service", "UUID", "PostgreSQL"]
}
```

The response is cached in Redis with a 1-hour TTL under the key `query:{orgId}:{questionHash}`.

## Failure Handling

If the embedding-service fails to call Ollama (timeout, model not loaded), it retries up to three times with exponential backoff. After three failures, the event is published to `contextengine.knowledge.errors` with the failure reason and the original event payload. A monitoring alert fires on this topic. The event can be replayed manually once the dependency recovers.

If Neo4j is unavailable, the knowledge-graph-service logs the failure and continues — graph enrichment is best-effort and non-blocking for the embedding pipeline. Resilience4j circuit breakers prevent cascading failures from a Neo4j outage from impacting ingestion throughput.

If Redis is unavailable, the query-service bypasses the cache and continues to answer queries directly. Cache miss behavior is the degraded-mode fallback, not a failure.

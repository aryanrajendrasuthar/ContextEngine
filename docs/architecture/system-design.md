
# ContextEngine System Design

## Overview

ContextEngine is an enterprise knowledge intelligence platform that continuously ingests organizational knowledge from tools like GitHub, Slack, Jira, and Confluence, then makes that knowledge queryable through natural language. The system is designed to solve a problem that every growing engineering organization faces: knowledge fragmentation. Decisions get made in Slack threads that nobody can find six months later. Engineers leave and take their context with them. New hires spend weeks piecing together why the codebase looks the way it does.

ContextEngine addresses this by treating knowledge as a first-class data product. Every pull request, every Slack message, every Jira ticket is ingested, semantically indexed, and woven into a knowledge graph. When an engineer asks "why did we switch from REST to gRPC for the payment service?", the system retrieves the relevant Slack discussion, the ADR, and the pull request that implemented it — and synthesizes a grounded answer with citations.

## Architecture

The system follows a microservices architecture with event-driven ingestion. Each service has a single, well-defined responsibility. Services communicate asynchronously through Kafka for data pipelines and synchronously through REST for request-response interactions. The API gateway handles all inbound traffic, authentication, and routing.

```
                        +------------------+
                        |   React Frontend  |
                        +--------+---------+
                                 |
                        +--------+---------+
                        |   API Gateway    |  :8080
                        | (Spring Cloud)   |
                        +--+--+--+--+------+
                           |  |  |  |
          +----------------+  |  |  +--------------------+
          |                   |  |                       |
 +--------+--------+  +-------+------+  +---------------++  +---------------+
 | user-service    |  | query-service|  | ingestion-svc  |  | connector-svc  |
 |    :8081        |  |    :8084     |  |    :8082       |  |    :8083       |
 +--------+--------+  +-------+------+  +-------+--------+  +-------+-------+
          |                   |                  |                   |
          |           +-------+-------+          |                   |
          |           | knowledge-    |          |                   |
          |           | graph-service |          |                   |
          |           |    :8085      |          |                   |
          |           +-------+-------+          |                   |
          |                   |                  |                   |
          |                +--+--+               |                   |
          |                |Kafka|<--------------+-----------+-------+
          |                +--+--+   contextengine.knowledge.raw
          |                   |
          |         +---------+---------+
          |         | embedding-service |
          |         |  (Python :8086)   |
          |         +---------+---------+
          |                   |
    +-----+-----+     +-------+-------+     +----------+
    | PostgreSQL|     |    Qdrant     |     |   Neo4j  |
    |    :5432  |     |    :6333      |     |   :7474  |
    +-----------+     +---------------+     +----------+
          |
    +-----+-----+
    |   Redis   |
    |    :6379  |
    +-----------+
```

## Services

### API Gateway (port 8080)

Built with Spring Cloud Gateway, the gateway is the single entry point for all client traffic. It handles JWT validation, request routing, rate limiting, and CORS. Every inbound request passes through here before reaching any downstream service. The gateway does not contain business logic — it routes, authenticates, and enforces policy.

### User Service (port 8081)

Manages authentication, organizations, teams, and API keys. Issues and validates JWT tokens. In Sprint 5 this service integrates with Keycloak for enterprise SSO (OAuth2/OIDC). Organization-level data isolation is enforced at this layer — every other service receives the authenticated organization ID from the JWT and filters all data by it.

### Ingestion Service (port 8082)

The entry point for all knowledge data. Accepts KnowledgeEvents via REST from the connector service, validates them, deduplicates by content hash, and publishes to Kafka. This service is stateless and horizontally scalable. It does not process or store knowledge — it only validates and routes.

### Connector Service (port 8083)

A plugin-based framework of connectors that pull knowledge from external sources — GitHub pull requests and commit messages, Slack messages, Jira tickets, Confluence pages, and generic webhooks. Each connector implements a common interface. The connector service polls or receives webhooks from external systems, normalizes the data into a standard KnowledgeEvent schema, and sends it to the ingestion service. Mock connectors with synthetic data are used in development to remove dependency on external API credentials.

### Query Service (port 8084)

The natural language query engine. When a user asks a question, this service orchestrates the full RAG pipeline: embedding the question, retrieving semantically similar knowledge chunks from Qdrant, fetching graph context from Neo4j (who made this decision, what else relates to it), assembling a prompt with the retrieved context, and calling the LLM to produce a grounded answer with source citations. Results are cached in Redis for one hour per organization.

### Knowledge Graph Service (port 8085)

Consumes processed knowledge events from Kafka and builds the Neo4j knowledge graph. Runs entity extraction using spaCy to identify people, technical concepts, and decisions in document text. Creates nodes (Person, Document, Concept, Decision) and relationships (AUTHORED, REFERENCES, DECIDED, PARTICIPATED_IN). The graph enables graph-traversal queries like "who else worked on systems related to the auth service?" that pure vector search cannot answer.

### Embedding Service (port 8086, Python FastAPI)

The AI processing pipeline. Consumes raw knowledge events from Kafka, chunks long documents into overlapping 512-token segments with metadata preservation, generates vector embeddings using Ollama (nomic-embed-text model), and stores them in Qdrant. Publishes processed events with Qdrant point IDs to the downstream Kafka topic. Failed events go to a dead letter queue for inspection and replay.

## Data Stores

### PostgreSQL

The primary relational store. Holds users, organizations, connectors, knowledge source metadata, audit logs, ingestion job records, and query history. Schema migrations are managed by Flyway, ensuring zero-downtime deployments. All tables use row-level security policies to enforce organization-level data isolation.

### Qdrant

The vector database. Stores semantic embeddings of all knowledge chunks, one collection per organization for tenant isolation. Enables cosine similarity search across hundreds of thousands of document chunks in under 50ms. Qdrant was chosen over pgvector for its dedicated vector index structures (HNSW), superior query performance at scale, and built-in payload filtering.

### Neo4j Community Edition

The knowledge graph database. Stores structured relationships between entities extracted from knowledge events. Enables traversal queries that vector search cannot — finding all decisions made by a departing engineer, or all Slack threads that reference a particular service. Neo4j Community Edition provides all required graph features at zero cost.

### Redis

Used for query response caching (1-hour TTL per organization + question hash), session state, rate limiting counters, and distributed locks. All shared state that would otherwise prevent horizontal scaling lives in Redis.

## Infrastructure

All services run locally via Docker Compose for development. Production deployment uses Kubernetes manifests and a Helm chart, built in Sprint 6. The LLM and embedding model run locally via Ollama (nomic-embed-text for embeddings, llama3.1:8b for query answering), eliminating all external API dependencies and associated costs.

Kafka handles all asynchronous communication between services. Topics use a minimum of three partitions to enable parallel consumer scaling. The following topics are used:

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| contextengine.knowledge.raw | ingestion-service | embedding-service | Raw validated knowledge events |
| contextengine.knowledge.processed | embedding-service | knowledge-graph-service | Events with Qdrant point IDs |
| contextengine.knowledge.errors | embedding-service | ops monitoring | Failed processing events |

## Security

Authentication uses JWT tokens issued by the user-service and validated at the gateway. In Sprint 5, Keycloak replaces the custom JWT issuer for enterprise-grade SSO. All tokens carry the organization ID as a claim. Every service that queries a data store filters by this organization ID. Row-level security in PostgreSQL provides a secondary enforcement layer.

## Performance Targets

All targets are hard requirements, not aspirational. Architecture decisions are made with these constraints in mind from day one.

| Metric | Target |
|---|---|
| REST API p99 latency | < 200ms |
| Query endpoint p99 latency | < 500ms |
| Kafka producer acknowledgment | < 50ms |
| Redis cache read | < 5ms |
| Vector search (100k documents) | < 50ms |
| Knowledge graph 3-hop traversal | < 100ms |
| Query end-to-end (including LLM) | p99 < 3 seconds |
| Ingestion throughput | 1,000 events/second per partition |
| Embedding throughput | 200 documents/minute |
| Concurrent query capacity | 100 users without degradation |

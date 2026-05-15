
# ADR-001: Qdrant as the Vector Database

**Status:** Accepted
**Date:** 2024-05-14
**Authors:** Platform Engineering Team

## Context

ContextEngine requires a vector database to store and query semantic embeddings at scale. The system must support:

- Storage of millions of 768-dimensional vectors (nomic-embed-text output) and 1536-dimensional vectors (for future OpenAI compatibility)
- Filtered nearest-neighbor search by organization ID, source type, date range, and author within milliseconds
- Multi-tenant isolation without cross-tenant data leakage
- Horizontal scalability to handle growing knowledge bases
- Zero-cost operation for local development and self-hosted production deployments

The following options were evaluated: Qdrant Community Edition, pgvector (PostgreSQL extension), Weaviate, Pinecone, and Chroma.

## Decision

We will use Qdrant Community Edition as the vector database.

## Rationale

**pgvector** is the obvious first choice for teams already using PostgreSQL — no new infrastructure, familiar SQL, single data store. However, pgvector's query performance degrades meaningfully as vector counts exceed 100,000 rows. It lacks native support for filtered vector search using HNSW indexes (adding a WHERE clause forces a full table scan pre-filter or a post-filter that degrades recall), and it does not support horizontal scaling. For a knowledge management platform where a single large organization could have millions of indexed chunks, pgvector is not viable as the primary vector store.

**Pinecone** is a managed cloud service with best-in-class performance. It is also not free. The starter tier is limited in vector count and does not support the filtering capabilities required for multi-tenant isolation. Any serious usage requires a paid subscription. This is disqualifying given the project's zero-budget constraint.

**Weaviate** is open source and capable. Its operational complexity is higher than Qdrant — it requires more configuration, its module system for embedding generation adds coupling, and its community is smaller. For a project that needs to move quickly, Weaviate's operational overhead is not justified over Qdrant.

**Chroma** is optimized for prototyping and small-scale local use. It lacks production-grade features: no built-in tenant isolation, limited payload filtering, and no horizontal scaling path. It is the right tool for a demo; it is not the right tool here.

**Qdrant** provides dense HNSW indexing, native payload filtering that executes as part of the vector search (not as a post-filter), built-in collection-per-tenant isolation, a simple REST and gRPC API, and a Docker image that runs entirely locally without external dependencies. Query performance for filtered search across 100,000+ vectors is consistently under 50ms in benchmarks. The Community Edition is fully open source under Apache 2.0 and has no feature restrictions relative to the commercial offering for our use case.

## One collection per organization

Qdrant supports filtering within a single collection by payload fields, which could support multi-tenancy in a single collection using an `organizationId` filter on every query. We chose collection-per-organization instead for two reasons.

First, it provides hard isolation — a bug in the query layer that forgets to include the organization filter cannot leak data across tenants. Defense in depth matters for enterprise customers.

Second, it avoids index contention. A single collection for all organizations would concentrate all write load on one HNSW index. Separate collections distribute that load.

The downside is operational overhead when an organization is created (a new collection must be provisioned) or deleted (the collection must be dropped). This is a one-time operation per organization lifecycle event, not a hot path.

## Consequences

- The embedding-service must create a Qdrant collection on first knowledge ingestion for a new organization.
- All Qdrant queries must include the collection name derived from the organization ID.
- Vector dimensionality must be set at collection creation time. The collection is created with 768 dimensions for nomic-embed-text. If embedding models are changed in the future, existing collections must be migrated.
- Qdrant does not support SQL, so all queries are expressed through the Qdrant client library or REST API. Engineers not familiar with Qdrant will need to learn its filter syntax.

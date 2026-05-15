
# ADR-002: Neo4j for the Knowledge Graph

**Status:** Accepted
**Date:** 2024-05-14
**Authors:** Platform Engineering Team

## Context

ContextEngine needs to model relationships between people, documents, concepts, and decisions in a way that supports multi-hop traversal queries. The core question is: when a user asks "who in this company knows the most about the payment service's rate limiting behavior?", the system needs to traverse a graph — from the concept "rate limiting", to all documents that mention it, to the people who authored those documents, weighted by how many documents each person has contributed. This is a graph traversal problem.

The question is whether to use a dedicated graph database or to model the graph in the existing PostgreSQL instance using adjacency tables.

## Decision

We will use Neo4j Community Edition as the graph database.

## Rationale

The adjacency table approach in PostgreSQL is well-understood. A `relationships` table with `source_id`, `target_id`, `type`, and `properties` columns can model a graph. For simple, shallow traversals this works. For multi-hop queries — "find all people connected to this concept through documents within three hops" — recursive CTEs become complex to write correctly, and their performance degrades with path depth and graph density because PostgreSQL must re-evaluate indexes at each hop.

Neo4j's query engine is built specifically for graph traversal. It maintains a native graph storage format where each node directly stores pointers to its adjacent nodes, meaning multi-hop traversals are pointer-following operations rather than join operations. A 3-hop traversal that would require three self-joins in SQL is expressed in Cypher as a single pattern match with a depth parameter, and executes in under 100ms even on large graphs.

Beyond performance, Neo4j's Cypher query language makes graph traversal intent explicit. A query that expresses "find all decisions made by people who worked in the same channels as a given person" is readable in Cypher in a way that a recursive CTE is not. This matters for maintenance — the query-service and knowledge-graph-service will have complex traversal logic, and code that reads clearly is code that can be debugged and extended.

Neo4j Community Edition is fully open source, runs in Docker, and provides all features required: full CRUD, the Cypher query language, APOC procedures, full-text search, and the native graph storage engine. The Enterprise Edition adds multi-clustering and commercial support, which are not required for this project. The Community Edition has no vector search capability, but that is intentional — Qdrant handles vectors, Neo4j handles relationships.

The alternative graph database evaluated was Amazon Neptune (managed, expensive, cloud-only — disqualified immediately) and ArangoDB (multi-model graph + document store). ArangoDB is capable but its query language (AQL) is less mature than Cypher and its community is smaller, meaning fewer resources when debugging unusual behavior.

## Consequences

- Neo4j runs in Docker on port 7474 (HTTP browser) and 7687 (Bolt protocol, used by the Java driver).
- The Spring Boot services use the Spring Data Neo4j dependency and the official Neo4j Java Driver.
- All graph queries must include an `organizationId` filter. This is enforced by convention in the repository layer.
- Schema migrations for Neo4j are handled by creating indexes and constraints in an initialization service on startup, since Neo4j does not use Flyway. This is a divergence from the PostgreSQL migration approach and must be documented clearly.
- Neo4j Community Edition does not support read replicas. For read-heavy production deployments, the architecture would need to be revisited with Neo4j Enterprise or a different graph database. For the current scale, a single Neo4j Community instance with adequate memory (8–16GB heap) is sufficient.

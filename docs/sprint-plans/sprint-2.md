
# Sprint 2: Connector Framework and Knowledge Ingestion

**Duration:** Day 2, approximately 3 hours
**Status:** Planned

## Goal

Build the connector framework that pulls knowledge from external sources and the ingestion pipeline that processes and streams that knowledge into the system.

## Deliverables

- connector-service: Plugin-based connector framework with a common ConnectorInterface
- GitHub connector: Pull requests, commit messages via GitHub REST API
- Slack connector: Messages from configured channels via Slack Web API
- Jira connector: Tickets, comments, status history via Jira REST API
- Generic webhook connector for arbitrary structured knowledge events
- KnowledgeEvent schema: sourceId, sourceType, content, authorId, authorName, timestamp, url, metadata
- ingestion-service: Validates KnowledgeEvents, deduplicates by content hash, publishes to Kafka
- PostgreSQL schema: knowledge_sources, knowledge_events, connectors, ingestion_jobs tables
- Connector configuration API: CRUD endpoints per organization
- Kafka topics: contextengine.knowledge.raw, contextengine.knowledge.processed, contextengine.knowledge.errors
- Full OpenAPI documentation for connector and ingestion APIs
- Unit tests and integration tests with Testcontainers
- LEARNING.md Sprint 2 section

## Commit Checkpoints

- CHECKPOINT 2A: Connector framework and GitHub + Slack connectors
- CHECKPOINT 2B: Jira connector, webhook connector, ingestion-service
- CHECKPOINT 2C: PostgreSQL schema, connector config API, tests

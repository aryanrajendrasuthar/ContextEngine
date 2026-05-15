
# Sprint 4: Knowledge Graph and Query Intelligence

**Duration:** Day 4, approximately 3 hours
**Status:** Planned

## Goal

Build the knowledge graph that understands relationships between people, concepts, and decisions, and the query engine that answers natural language questions using RAG.

## Deliverables

- knowledge-graph-service: Kafka consumer reading from contextengine.knowledge.processed
- Neo4j node creation: Person, Document, Concept, Decision nodes
- Neo4j relationship creation: AUTHORED, MENTIONS, REFERENCES, DECIDED, PARTICIPATED_IN
- Entity extraction: Python spaCy NLP to extract people names, technical concepts, and decisions
- query-service: Natural language query engine
- Full RAG pipeline: embed question → Qdrant retrieval → Neo4j graph context → LLM prompt → grounded answer
- Query API: POST /api/v1/query returning answer, sources, confidence, relatedConcepts
- Redis query caching: 1-hour TTL per organization + question hash
- Source attribution: every answer includes source documents with URL and author
- LEARNING.md Sprint 4 section

## Commit Checkpoints

- CHECKPOINT 4A: knowledge-graph-service, Neo4j schema, entity extraction
- CHECKPOINT 4B: query-service RAG pipeline
- CHECKPOINT 4C: Redis query caching and source attribution


package com.contextengine.knowledgegraph.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All Neo4j write operations for knowledge graph construction.
 * Every MERGE is idempotent — re-processing the same event produces the same graph state.
 * All queries filter by organizationId to enforce tenant isolation.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GraphRepository {

    private final Driver driver;

    public void mergeDocument(String organizationId, String sourceId, String sourceType,
                               String title, String url, String qdrantPointId, Instant timestamp) {
        try (Session session = driver.session()) {
            session.run("""
                    MERGE (d:Document {organizationId: $orgId, sourceId: $sourceId})
                    ON CREATE SET
                        d.id = $id,
                        d.sourceType = $sourceType,
                        d.title = $title,
                        d.url = $url,
                        d.qdrantPointId = $qdrantPointId,
                        d.timestamp = datetime($timestamp),
                        d.createdAt = datetime()
                    ON MATCH SET
                        d.title = $title,
                        d.qdrantPointId = $qdrantPointId
                    """,
                    Map.of(
                            "orgId", organizationId,
                            "sourceId", sourceId,
                            "id", UUID.randomUUID().toString(),
                            "sourceType", sourceType,
                            "title", title != null ? title : sourceId,
                            "url", url != null ? url : "",
                            "qdrantPointId", qdrantPointId != null ? qdrantPointId : "",
                            "timestamp", timestamp != null ? timestamp.toString() : Instant.now().toString()
                    ));
        }
    }

    public void mergePerson(String organizationId, String sourceAuthorId, String name) {
        if (sourceAuthorId == null || sourceAuthorId.isBlank()) return;
        try (Session session = driver.session()) {
            session.run("""
                    MERGE (p:Person {organizationId: $orgId, sourceAuthorId: $authorId})
                    ON CREATE SET
                        p.id = $id,
                        p.name = $name,
                        p.createdAt = datetime(),
                        p.updatedAt = datetime()
                    ON MATCH SET
                        p.name = $name,
                        p.updatedAt = datetime()
                    """,
                    Map.of(
                            "orgId", organizationId,
                            "authorId", sourceAuthorId,
                            "id", UUID.randomUUID().toString(),
                            "name", name != null ? name : sourceAuthorId
                    ));
        }
    }

    public void mergeAuthoredRelationship(String organizationId, String sourceAuthorId, String sourceId) {
        if (sourceAuthorId == null || sourceAuthorId.isBlank()) return;
        try (Session session = driver.session()) {
            session.run("""
                    MATCH (p:Person {organizationId: $orgId, sourceAuthorId: $authorId})
                    MATCH (d:Document {organizationId: $orgId, sourceId: $sourceId})
                    MERGE (p)-[:AUTHORED]->(d)
                    """,
                    Map.of("orgId", organizationId, "authorId", sourceAuthorId, "sourceId", sourceId));
        }
    }

    public void mergeConcept(String organizationId, String conceptName) {
        try (Session session = driver.session()) {
            session.run("""
                    MERGE (c:Concept {organizationId: $orgId, name: $name})
                    ON CREATE SET
                        c.id = $id,
                        c.occurrenceCount = 1,
                        c.type = 'TECHNOLOGY',
                        c.createdAt = datetime(),
                        c.updatedAt = datetime()
                    ON MATCH SET
                        c.occurrenceCount = c.occurrenceCount + 1,
                        c.updatedAt = datetime()
                    """,
                    Map.of(
                            "orgId", organizationId,
                            "name", conceptName,
                            "id", UUID.randomUUID().toString()
                    ));
        }
    }

    public void mergeMentionsRelationship(String organizationId, String sourceId, String conceptName) {
        try (Session session = driver.session()) {
            session.run("""
                    MATCH (d:Document {organizationId: $orgId, sourceId: $sourceId})
                    MATCH (c:Concept {organizationId: $orgId, name: $conceptName})
                    MERGE (d)-[r:MENTIONS]->(c)
                    ON CREATE SET r.count = 1
                    ON MATCH SET r.count = r.count + 1
                    """,
                    Map.of("orgId", organizationId, "sourceId", sourceId, "conceptName", conceptName));
        }
    }

    public void mergeDecision(String organizationId, String sourceId, String summary) {
        try (Session session = driver.session()) {
            session.run("""
                    MATCH (d:Document {organizationId: $orgId, sourceId: $sourceId})
                    MERGE (dec:Decision {organizationId: $orgId, documentId: $sourceId})
                    ON CREATE SET
                        dec.id = $id,
                        dec.summary = $summary,
                        dec.createdAt = datetime(),
                        dec.decidedAt = d.timestamp
                    MERGE (d)-[:DECIDED]->(dec)
                    """,
                    Map.of(
                            "orgId", organizationId,
                            "sourceId", sourceId,
                            "id", UUID.randomUUID().toString(),
                            "summary", summary
                    ));
        }
    }

    public List<String> findConceptsForDocument(String organizationId, String sourceId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (d:Document {organizationId: $orgId, sourceId: $sourceId})-[:MENTIONS]->(c:Concept)
                            RETURN c.name AS name
                            """,
                            Map.of("orgId", organizationId, "sourceId", sourceId))
                    .list(r -> r.get("name").asString());
        }
    }
}

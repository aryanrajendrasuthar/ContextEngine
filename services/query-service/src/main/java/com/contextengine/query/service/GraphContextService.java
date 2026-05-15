
package com.contextengine.query.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Queries Neo4j for context that enriches RAG answers: related concepts and
 * people who have authored documents on the same topics as the retrieved chunks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphContextService {

    private final Driver neo4jDriver;

    public record GraphContext(List<String> concepts, List<String> people) {}

    /**
     * Given the sourceIds of documents returned by Qdrant, find the concepts
     * and people connected to those documents in the knowledge graph.
     */
    public GraphContext getContextForSources(String organizationId, List<String> sourceIds) {
        if (sourceIds.isEmpty()) return new GraphContext(List.of(), List.of());

        try (Session session = neo4jDriver.session()) {
            List<String> concepts = session.run("""
                            MATCH (d:Document {organizationId: $orgId})-[:MENTIONS]->(c:Concept)
                            WHERE d.sourceId IN $sourceIds
                            RETURN DISTINCT c.name AS name
                            ORDER BY c.occurrenceCount DESC
                            LIMIT 10
                            """,
                            Map.of("orgId", organizationId, "sourceIds", sourceIds))
                    .list(r -> r.get("name").asString());

            List<String> people = session.run("""
                            MATCH (p:Person {organizationId: $orgId})-[:AUTHORED]->(d:Document)
                            WHERE d.sourceId IN $sourceIds
                            RETURN DISTINCT p.name AS name, count(d) AS docCount
                            ORDER BY docCount DESC
                            LIMIT 5
                            """,
                            Map.of("orgId", organizationId, "sourceIds", sourceIds))
                    .list(r -> r.get("name").asString());

            log.debug("Graph context: org={}, concepts={}, people={}", organizationId, concepts.size(), people.size());
            return new GraphContext(concepts, people);

        } catch (Exception e) {
            log.warn("Neo4j context query failed (graph may be empty): {}", e.getMessage());
            return new GraphContext(List.of(), List.of());
        }
    }
}

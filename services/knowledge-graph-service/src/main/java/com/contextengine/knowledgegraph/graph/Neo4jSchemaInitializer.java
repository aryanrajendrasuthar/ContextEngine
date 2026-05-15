
package com.contextengine.knowledgegraph.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates Neo4j indexes on startup if they do not already exist.
 * Uses IF NOT EXISTS so this is safe to run on every application start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jSchemaInitializer {

    private final Driver driver;

    private static final String[] INDEX_STATEMENTS = {
        "CREATE INDEX person_org_idx IF NOT EXISTS FOR (p:Person) ON (p.organizationId, p.sourceAuthorId)",
        "CREATE INDEX document_org_idx IF NOT EXISTS FOR (d:Document) ON (d.organizationId, d.sourceId)",
        "CREATE INDEX document_type_idx IF NOT EXISTS FOR (d:Document) ON (d.organizationId, d.sourceType)",
        "CREATE INDEX concept_name_idx IF NOT EXISTS FOR (c:Concept) ON (c.organizationId, c.name)",
        "CREATE INDEX decision_org_idx IF NOT EXISTS FOR (dec:Decision) ON (dec.organizationId)",
        "CREATE INDEX repository_org_idx IF NOT EXISTS FOR (r:Repository) ON (r.organizationId, r.fullName)",
    };

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSchema() {
        log.info("Initializing Neo4j schema indexes");
        try (Session session = driver.session()) {
            for (String statement : INDEX_STATEMENTS) {
                session.run(statement);
            }
            log.info("Neo4j schema initialization complete");
        } catch (Exception e) {
            log.warn("Neo4j schema initialization failed (Neo4j may not be running yet): {}", e.getMessage());
        }
    }
}

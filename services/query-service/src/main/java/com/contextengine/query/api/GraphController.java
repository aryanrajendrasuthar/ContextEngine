
package com.contextengine.query.api;

import com.contextengine.query.api.dto.GraphData;
import com.contextengine.query.api.dto.GraphEdge;
import com.contextengine.query.api.dto.GraphNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
@Tag(name = "Graph", description = "Knowledge graph exploration for visualization")
public class GraphController {

    private final Driver neo4jDriver;

    @GetMapping("/people")
    @Operation(summary = "Return all Person nodes and their document/concept connections for the People Graph view")
    public ResponseEntity<GraphData> getPeopleGraph(
            @RequestHeader("X-Organization-Id") String organizationId) {

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            session.run("""
                            MATCH (p:Person {organizationId: $orgId})-[:AUTHORED]->(d:Document)
                            OPTIONAL MATCH (d)-[:MENTIONS]->(c:Concept)
                            RETURN p.name AS person, p.authorId AS authorId,
                                   collect(DISTINCT d.sourceId) AS docs,
                                   collect(DISTINCT c.name) AS concepts,
                                   count(DISTINCT d) AS docCount
                            ORDER BY docCount DESC
                            LIMIT 50
                            """,
                            Map.of("orgId", organizationId))
                    .stream()
                    .forEach(record -> {
                        String personId = "person:" + record.get("person").asString();
                        Map<String, Object> personProps = new HashMap<>();
                        personProps.put("docCount", record.get("docCount").asLong());
                        nodes.add(new GraphNode(personId, record.get("person").asString(), "Person", personProps));

                        record.get("concepts").asList(v -> v.asString()).forEach(concept -> {
                            String conceptId = "concept:" + concept;
                            boolean exists = nodes.stream().anyMatch(n -> n.id().equals(conceptId));
                            if (!exists) {
                                nodes.add(new GraphNode(conceptId, concept, "Concept", Map.of()));
                            }
                            edges.add(new GraphEdge(personId, conceptId, "KNOWS_ABOUT"));
                        });
                    });
        } catch (Exception e) {
            log.warn("Neo4j people graph query failed: {}", e.getMessage());
        }

        return ResponseEntity.ok(new GraphData(nodes, edges));
    }

    @GetMapping("/explore")
    @Operation(summary = "Explore the knowledge graph — returns nodes and edges matching a keyword query")
    public ResponseEntity<GraphData> explore(
            @RequestParam String query,
            @RequestHeader("X-Organization-Id") String organizationId) {

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            session.run("""
                            MATCH (c:Concept {organizationId: $orgId})
                            WHERE toLower(c.name) CONTAINS toLower($query)
                            OPTIONAL MATCH (d:Document {organizationId: $orgId})-[:MENTIONS]->(c)
                            OPTIONAL MATCH (p:Person {organizationId: $orgId})-[:AUTHORED]->(d)
                            RETURN c.name AS concept,
                                   collect(DISTINCT {id: d.sourceId, type: d.sourceType, url: d.url}) AS docs,
                                   collect(DISTINCT p.name) AS authors
                            LIMIT 20
                            """,
                            Map.of("orgId", organizationId, "query", query))
                    .stream()
                    .forEach(record -> {
                        String conceptId = "concept:" + record.get("concept").asString();
                        nodes.add(new GraphNode(conceptId, record.get("concept").asString(), "Concept", Map.of()));

                        record.get("docs").asList(v -> v.asMap()).forEach(doc -> {
                            String docId = "doc:" + doc.get("id");
                            boolean exists = nodes.stream().anyMatch(n -> n.id().equals(docId));
                            if (!exists) {
                                Map<String, Object> props = new HashMap<>();
                                props.put("sourceType", doc.get("type"));
                                props.put("url", doc.get("url"));
                                nodes.add(new GraphNode(docId, String.valueOf(doc.get("id")), "Document", props));
                            }
                            edges.add(new GraphEdge(docId, conceptId, "MENTIONS"));
                        });

                        record.get("authors").asList(v -> v.asString()).forEach(author -> {
                            String personId = "person:" + author;
                            boolean exists = nodes.stream().anyMatch(n -> n.id().equals(personId));
                            if (!exists) {
                                nodes.add(new GraphNode(personId, author, "Person", Map.of()));
                            }
                            record.get("docs").asList(v -> v.asMap()).forEach(doc ->
                                    edges.add(new GraphEdge(personId, "doc:" + doc.get("id"), "AUTHORED")));
                        });
                    });
        } catch (Exception e) {
            log.warn("Neo4j explore query failed: {}", e.getMessage());
        }

        return ResponseEntity.ok(new GraphData(nodes, edges));
    }
}

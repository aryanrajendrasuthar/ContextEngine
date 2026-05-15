
package com.contextengine.knowledgegraph.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts technical concepts and decision signals from knowledge event content
 * using keyword matching and pattern detection.
 *
 * This approach is deterministic and testable. The LLM handles semantic nuance;
 * this layer handles structural labeling for graph construction.
 */
@Component
public class EntityExtractor {

    private static final Set<String> TECH_CONCEPTS = Set.of(
            "postgresql", "postgres", "redis", "kafka", "qdrant", "neo4j",
            "keycloak", "docker", "kubernetes", "k8s", "spring boot", "spring",
            "jwt", "oauth", "oauth2", "oidc", "saml", "flyway", "hikaricp",
            "resilience4j", "circuit breaker", "circuit-breaker",
            "grpc", "rest api", "graphql", "websocket",
            "react", "typescript", "vite", "tailwind",
            "ollama", "llm", "rag", "embedding", "vector search", "qdrant",
            "uuid", "idempotency", "idempotent", "deduplication",
            "kafka producer", "kafka consumer", "dead letter queue", "dlq",
            "hnsw", "cosine similarity", "semantic search",
            "microservice", "monolith", "api gateway", "load balancer",
            "pgbouncer", "connection pool", "connection pooling",
            "prometheus", "grafana", "opentelemetry", "tracing",
            "helm", "terraform", "ci/cd", "github actions"
    );

    private static final Pattern DECISION_PATTERN = Pattern.compile(
            "\\b(we decided|team decided|decided to use|decided to|we will use|" +
            "going forward|we agreed|we chose|chosen over|we're moving to|" +
            "we are moving to|migration plan|the decision is|" +
            "we're adopting|we are adopting|strategy is|approach is)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public record ExtractionResult(List<String> concepts, boolean containsDecision) {}

    public ExtractionResult extract(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();

        for (String concept : TECH_CONCEPTS) {
            if (lower.contains(concept)) {
                found.add(concept);
            }
        }

        boolean isDecision = DECISION_PATTERN.matcher(content).find();
        return new ExtractionResult(found, isDecision);
    }
}

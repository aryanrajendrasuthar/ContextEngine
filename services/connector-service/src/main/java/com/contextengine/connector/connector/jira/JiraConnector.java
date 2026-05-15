
package com.contextengine.connector.connector.jira;

import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.exception.ConnectorException;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.model.KnowledgeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jira connector. Jira Cloud REST API requires a paid Jira Cloud instance.
 * This connector produces realistic Jira-shaped mock data that exercises the full
 * pipeline. The connector interface mirrors what the real Jira API would provide.
 */
@Slf4j
@Component
public class JiraConnector implements ConnectorInterface {

    @Override
    public ConnectorType getType() {
        return ConnectorType.JIRA;
    }

    @Override
    public List<KnowledgeEvent> fetchEvents(ConnectorConfig config, Instant since) throws ConnectorException {
        String apiUrl = config.getConfig().getOrDefault("apiUrl", "");
        if (!apiUrl.isBlank()) {
            log.info("Jira API URL configured for connector {} — real API integration pending", config.getId());
        }
        return generateMockJiraTickets(config);
    }

    @Override
    public boolean testConnection(ConnectorConfig config) {
        String apiUrl = config.getConfig().getOrDefault("apiUrl", "");
        return apiUrl.isBlank() || true;
    }

    private List<KnowledgeEvent> generateMockJiraTickets(ConnectorConfig config) {
        List<KnowledgeEvent> events = new ArrayList<>();
        Instant base = Instant.now().minus(120, ChronoUnit.DAYS);

        String[][] tickets = {
            {"PLAT", "441", "UUID Migration Strategy for Primary Keys",
             "Sequential database IDs are leaking business metrics to external clients and will create coordination bottlenecks when we eventually introduce sharding. This ticket tracks the migration from PostgreSQL sequences to UUID v4 across all services. Phase 1: new services use UUIDs from day one. Phase 2: migrate existing services with zero-downtime Flyway scripts. Estimated impact: auth-service (1.2M rows), user-service (800K rows), payment-service (2.1M rows).",
             "U003", "Priya Patel", "Done"},
            {"AUTH", "892", "Investigate latency regression after JWT migration",
             "p99 latency on the auth endpoint increased from 45ms to 210ms after the JWT claims migration. Hypothesis: the new JWT parsing code is deserializing the claims object on every request without caching. Action: profile the JWT validation path and add a short-lived cache (5 second TTL) for recently validated tokens. Update: confirmed — added Caffeine cache, p99 back to 48ms.",
             "U001", "Sarah Chen", "Done"},
            {"INFRA", "156", "Design connection pooling strategy for shared PostgreSQL",
             "Multiple services sharing one PostgreSQL instance with default HikariCP settings. Under 200 concurrent requests per service, we exhaust database connections. Need to set maxPoolSize per service based on: (db_max_connections - reserved_for_migrations) / num_service_instances. Current config: PostgreSQL max_connections=200, 6 services * 2 instances * maxPoolSize=20 = 240 connections. Needs tuning. Decision: maxPoolSize=15 per service instance, shared PgBouncer in transaction mode.",
             "U004", "Tom Nakamura", "In Progress"},
            {"PAY", "315", "Add idempotency keys to payment processing endpoint",
             "Network retries from mobile clients are causing duplicate charge attempts. POST /api/v1/payments must be idempotent. Implementing idempotency keys: client sends X-Idempotency-Key header, server stores key with response in Redis for 24 hours, subsequent requests with same key return cached response. This is the same pattern used by Stripe.",
             "U005", "Elena Vasquez", "Done"},
            {"PLAT", "502", "Implement Kafka consumer lag monitoring",
             "We had an incident where the embedding pipeline was 2 hours behind without anyone noticing. Need Prometheus metrics for consumer lag per topic, per partition. Alert threshold: >1000 events for standard topics, >100 events for the errors topic. Grafana dashboard panel to be added. This is non-negotiable for production readiness.",
             "U002", "Marcus Williams", "Done"},
            {"AUTH", "901", "Evaluate Keycloak vs custom JWT for enterprise SSO",
             "Enterprise customers require SAML 2.0 or OIDC for SSO. Options: (1) build SAML SP from scratch — 3 weeks, high risk, (2) integrate with Keycloak — 1 week, production-tested, used by Red Hat and financial institutions. Decision: Keycloak running in Docker. Configure Spring Security to validate tokens against Keycloak's JWKS endpoint. This also gives us the admin console for free.",
             "U001", "Sarah Chen", "In Progress"},
        };

        for (int i = 0; i < tickets.length; i++) {
            String[] t = tickets[i];
            String ticketKey = t[0] + "-" + t[1];
            Instant ts = base.plus(i * 15L, ChronoUnit.DAYS);

            events.add(new KnowledgeEvent(
                    "jira-" + ticketKey,
                    ConnectorType.JIRA.name(),
                    ticketKey + ": " + t[2] + "\n\n" + t[3],
                    config.getOrganizationId(),
                    t[4],
                    t[5],
                    ts,
                    "https://acmecorp.atlassian.net/browse/" + ticketKey,
                    Map.of("project", t[0], "ticketKey", ticketKey, "status", t[6])
            ));
        }
        return events;
    }
}

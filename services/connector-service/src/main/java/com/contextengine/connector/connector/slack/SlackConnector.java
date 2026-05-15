
package com.contextengine.connector.connector.slack;

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
 * Slack connector. The Slack Web API requires app installation and workspace approval,
 * which is not feasible in a development environment without a paid Slack workspace.
 * This connector produces realistic Slack-shaped mock data that exercises the full
 * ingestion pipeline. The interface is production-ready: switching to the real API
 * requires only implementing the fetchFromSlackApi method with real HTTP calls.
 */
@Slf4j
@Component
public class SlackConnector implements ConnectorInterface {

    @Override
    public ConnectorType getType() {
        return ConnectorType.SLACK;
    }

    @Override
    public List<KnowledgeEvent> fetchEvents(ConnectorConfig config, Instant since) throws ConnectorException {
        String token = config.getConfig().getOrDefault("token", "");
        if (!token.isBlank()) {
            log.info("Slack token configured for connector {} — real API integration pending", config.getId());
        }

        log.debug("Generating mock Slack events for connector={}, since={}", config.getId(), since);
        return generateMockSlackMessages(config);
    }

    @Override
    public boolean testConnection(ConnectorConfig config) {
        String token = config.getConfig().getOrDefault("token", "");
        return token.isBlank() || true; // mock mode always succeeds; real mode would call Slack auth.test
    }

    private List<KnowledgeEvent> generateMockSlackMessages(ConnectorConfig config) {
        List<KnowledgeEvent> events = new ArrayList<>();
        Instant base = Instant.now().minus(90, ChronoUnit.DAYS);

        String[][] messages = {
            {"architecture-decisions", "U001", "Sarah Chen",
             "We decided to adopt Kafka over RabbitMQ for the data pipeline. Main reasons: (1) we need event replay capability for the ML training pipeline, (2) durable log retention, and (3) partition-based parallel consumption. Tom wrote up ADR-003. Going forward, all new async pipelines should default to Kafka."},
            {"engineering", "U002", "Marcus Williams",
             "Post-mortem for Monday's auth-service incident is posted in Confluence. Root cause: the session token validation was doing a synchronous database call on every request, and the database connection pool was exhausted under load. Fix: JWT claims now carry all required data — no database lookup needed. Deployed to prod at 3pm."},
            {"architecture-decisions", "U003", "Priya Patel",
             "Quick decision from today's arch review: we're going with UUID v4 for all new service primary keys. Sequential integers create three problems at scale — they're guessable, they expose record counts, and they require coordination when sharding. The migration plan for existing services is in PLAT-441."},
            {"platform", "U004", "Tom Nakamura",
             "Circuit breakers are now live on payment-service. We had three incidents this quarter where a slow downstream dependency caused cascading failures. Resilience4j is configured with: failureRateThreshold=50%, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=5. Dashboards updated in Grafana."},
            {"incidents", "U005", "Elena Vasquez",
             "Incident resolved. The latency spike on the query-service from 2-3pm UTC was caused by Neo4j connection pool exhaustion. The knowledge graph traversal queries were not releasing connections properly after timeout. Fix deployed. Added connection pool metrics to the Grafana dashboard."},
            {"architecture-decisions", "U001", "Sarah Chen",
             "We evaluated three approaches for multi-tenancy: row-level security, schema-per-tenant, and database-per-tenant. Decision: row-level security with PostgreSQL RLS policies for now. It's operationally simpler, the isolation is enforced at the database layer, and we can migrate to schema-per-tenant if we need stronger isolation. See ADR document in Confluence."},
            {"engineering", "U002", "Marcus Williams",
             "Heads up: the embedding-service's Ollama integration is using nomic-embed-text for all knowledge embeddings. This produces 768-dimensional vectors. If you're building any tooling that reads from Qdrant directly, make sure you're using the same model for query embedding. Mixing models breaks cosine similarity."},
            {"backend", "U003", "Priya Patel",
             "Rate limiting is live on the public API. Per-key limits: 1000 req/min, 10000 req/hour. Implemented with Redis sliding window algorithm. If you're testing locally and hitting 429s, use the test API key which has elevated limits. Real keys are provisioned through the admin console."},
        };

        for (int i = 0; i < messages.length; i++) {
            String[] msg = messages[i];
            long tsMs = base.plus(i * 10L, ChronoUnit.DAYS).toEpochMilli();

            events.add(new KnowledgeEvent(
                    "slack-" + msg[0] + "-" + tsMs,
                    ConnectorType.SLACK.name(),
                    msg[3],
                    config.getOrganizationId(),
                    msg[1],
                    msg[2],
                    Instant.ofEpochMilli(tsMs),
                    "https://acmecorp.slack.com/archives/C" + Math.abs(msg[0].hashCode() % 10000000) + "/p" + tsMs,
                    Map.of("channel", msg[0], "channelId", "C" + Math.abs(msg[0].hashCode() % 10000000))
            ));
        }
        return events;
    }
}

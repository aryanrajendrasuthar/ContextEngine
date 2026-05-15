
package com.contextengine.connector.connector.github;

import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.model.KnowledgeEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Produces realistic GitHub PR events when no real repository is configured.
 * The generated events are stable across calls for the same config — the same events
 * are always returned, and the ingestion service's deduplication handles re-sends.
 */
public final class GitHubMockData {

    private GitHubMockData() {}

    private static final String[][] PR_DATA = {
        {"Migrate auth-service from REST to gRPC for internal calls",
         "Internal service calls were showing p99 latency of 180ms over REST/JSON. After migrating to gRPC/Protobuf, p99 dropped to 12ms. Protobuf serialization is approximately 6x faster for our payload sizes. Backward compatibility maintained through adapter layer for external clients."},
        {"Add circuit breaker to payment-service downstream calls",
         "During last week's outage, slow responses from the downstream payment processor caused connection pool exhaustion that cascaded across the entire payment-service. Resilience4j circuit breakers now open after 5 consecutive failures and reset after 30 seconds. Fallback behavior returns cached data where available."},
        {"Replace sequential IDs with UUIDs in user-service",
         "Sequential IDs expose internal record counts to clients and create coordination bottlenecks when we eventually shard. Migration script backfills existing records without downtime. UUID v4 generation is distributed and requires no database round-trip."},
        {"Add distributed tracing to api-gateway",
         "Integrates OpenTelemetry throughout the API gateway. Every inbound request generates a trace ID that propagates through all downstream calls via HTTP headers. Spans are exported to Jaeger. Resolves the issue where latency spikes were impossible to attribute to a specific service call."},
        {"Implement connection pool tuning for data-pipeline service",
         "Default HikariCP settings were causing connection timeout errors at 200 concurrent requests during load testing. Pool size is now calculated as: (database_max_connections / service_instances) * 0.8. Set to maxPoolSize=20 per instance with minimumIdle=5."},
        {"Refactor JWT validation to use claims instead of database lookups",
         "Every authenticated request was performing a database lookup to validate the session token. JWT claims now carry organizationId, userId, and permissions. Auth-service database load reduced by approximately 40%. Token expiry: 15 minutes access, 30 days refresh."},
        {"Add Kafka dead letter queue for failed embedding events",
         "Previously, embedding failures silently dropped events. All failed events now go to contextengine.knowledge.errors with the failure reason, original payload, and retry count. Monitoring alert fires when DLQ lag exceeds 100 events."},
        {"Implement rate limiting on public API endpoints",
         "Without rate limiting, a single misbehaving client could exhaust connection pool resources. Implemented sliding window rate limiting using Redis: 1000 req/min per API key, 10000 req/hour. Returns 429 with Retry-After header when exceeded."},
    };

    private static final String[][] AUTHORS = {
        {"101", "sarah-chen"},
        {"102", "marcus-w"},
        {"103", "priya-patel"},
        {"104", "tom-nakamura"},
        {"105", "elena-v"},
    };

    public static List<KnowledgeEvent> generateMockPullRequests(ConnectorConfig config) {
        List<KnowledgeEvent> events = new ArrayList<>();
        Instant base = Instant.now().minus(180, ChronoUnit.DAYS);

        for (int i = 0; i < PR_DATA.length; i++) {
            String[] author = AUTHORS[i % AUTHORS.length];
            String[] pr = PR_DATA[i];
            int prNumber = 800 + i;
            String repo = "acme/platform";

            events.add(new KnowledgeEvent(
                    "github-pr-acme-platform-" + prNumber,
                    ConnectorType.GITHUB.name(),
                    pr[0] + "\n\n" + pr[1],
                    config.getOrganizationId(),
                    author[0],
                    author[1],
                    base.plus(i * 14L, ChronoUnit.DAYS),
                    "https://github.com/" + repo + "/pull/" + prNumber,
                    Map.of("repository", repo, "prNumber", String.valueOf(prNumber), "state", "merged")
            ));
        }
        return events;
    }
}

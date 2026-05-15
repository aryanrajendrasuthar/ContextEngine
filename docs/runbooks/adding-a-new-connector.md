
# Adding a New Connector

This document walks through implementing a new data source connector from scratch. It covers the connector interface, the mock/real implementation pattern, testing, and deployment.

---

## What a connector does

A connector is responsible for two things:

1. **Pull-based polling:** On a configurable schedule, it fetches new or updated content from a source system and submits it to the ingestion service.
2. **Push-based webhooks:** It receives push events from the source system at `POST /api/v1/webhooks/{connectorId}` and immediately forwards them to ingestion.

Connectors do not process content — they only fetch it and hand it off. All chunking, embedding, and graph extraction happens downstream in the pipeline.

---

## Step 1: Implement ConnectorInterface

All connectors implement the `ConnectorInterface` in the connector-service:

```java
package com.contextengine.connector.plugin;

import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.RawEvent;

import java.util.List;

public interface ConnectorInterface {
    String getSourceType();
    boolean validateConfig(ConnectorConfig config);
    List<RawEvent> fetchEvents(ConnectorConfig config);
}
```

Create a new class in `connector-service/src/main/java/com/contextengine/connector/plugin/`:

```java
@Component
public class JiraConnector implements ConnectorInterface {

    @Override
    public String getSourceType() {
        return "JIRA";
    }

    @Override
    public boolean validateConfig(ConnectorConfig config) {
        Map<String, String> cfg = config.getConfig();
        return cfg.containsKey("baseUrl") && cfg.containsKey("apiToken") && cfg.containsKey("projectKey");
    }

    @Override
    public List<RawEvent> fetchEvents(ConnectorConfig config) {
        // Call the Jira REST API, build RawEvent objects, return them
        // ...
    }
}
```

Spring's component scan discovers all `@Component` beans that implement `ConnectorInterface` automatically. No registration step is needed.

---

## Step 2: Define the config schema

Connectors receive their configuration as a `Map<String, String>` stored as JSONB in the `connectors` table. Document the required and optional keys in a comment on your connector class:

```java
/**
 * Required config keys:
 *   baseUrl   — Jira instance URL, e.g. https://yourcompany.atlassian.net
 *   apiToken  — Personal access token with read scope
 *   projectKey — Jira project key, e.g. ENG
 *
 * Optional config keys:
 *   issueTypes — comma-separated list, defaults to "Bug,Story,Task"
 *   maxResults — max issues per poll, defaults to 100
 */
```

The `ConnectorConfigResponse` DTO automatically redacts values whose keys contain `token`, `secret`, `password`, or `apiKey` — the plain value is never exposed in API responses.

---

## Step 3: Build a RawEvent from source data

`RawEvent` is the canonical structure the connector returns:

```java
RawEvent event = new RawEvent();
event.setSourceId("jira-ENG-" + issue.getKey());  // stable, unique identifier
event.setSourceType("JIRA");
event.setContent(issue.getSummary() + "\n\n" + issue.getDescription());
event.setUrl("https://yourcompany.atlassian.net/browse/" + issue.getKey());
event.setAuthorId(issue.getReporter().getAccountId());
event.setAuthorName(issue.getReporter().getDisplayName());
event.setTimestamp(issue.getUpdated().toInstant());
event.setMetadata(Map.of(
    "issueType", issue.getIssueType().getName(),
    "status", issue.getStatus().getName(),
    "priority", issue.getPriority().getName()
));
```

The `sourceId` must be stable and unique within an organization. The ingestion service uses it for deduplication — if you submit the same `sourceId` with the same content hash, the event is silently dropped. If the content has changed (issue updated), it is processed again.

---

## Step 4: Handle webhook events

If the source system supports push webhooks, implement the webhook handler in `WebhookController`. The controller already handles the routing — you only need to parse the source-specific payload:

```java
// In WebhookController.handleWebhook():
case "JIRA":
    return jiraWebhookParser.parse(connectorConfig, payload);
```

Create a `JiraWebhookParser` that converts the Jira webhook JSON structure into a `RawEvent`.

---

## Step 5: Write tests

Every connector requires two test classes:

**Unit test** — verifies `validateConfig()` and `fetchEvents()` with a mocked HTTP client. Use `@MockBean` or constructor injection of a mocked `WebClient`.

**Integration test** — verifies the full pull cycle against a live (or WireMocked) API. Place in `src/test/java/.../connector/JiraConnectorIntegrationTest.java`.

At minimum, test:
- `validateConfig` returns false when required keys are missing
- `fetchEvents` returns the correct number of events for a known response
- `fetchEvents` handles pagination correctly (if applicable)
- `fetchEvents` returns an empty list when the source has no new content

---

## Step 6: Register the connector via API

Once deployed, create a connector configuration through the API:

```bash
curl -X POST http://localhost:8080/api/v1/connectors \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Organization-Id: $ORG_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Engineering Jira",
    "sourceType": "JIRA",
    "config": {
      "baseUrl": "https://yourcompany.atlassian.net",
      "apiToken": "your-api-token",
      "projectKey": "ENG"
    }
  }'
```

Then activate it:
```bash
curl -X POST http://localhost:8080/api/v1/connectors/{id}/activate \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Organization-Id: $ORG_ID"
```

The scheduler will begin polling on the next tick (default: 60 seconds after activation). You can verify by watching ingestion-service logs for new events from your connector.

---

## Checklist

Before submitting a connector for review:

- [ ] Implements `ConnectorInterface` and is annotated `@Component`
- [ ] `getSourceType()` returns a consistent, uppercase string (e.g. `"JIRA"`)
- [ ] `validateConfig()` checks all required keys and returns `false` (not throws) on invalid config
- [ ] `fetchEvents()` handles pagination if the API has limits
- [ ] `fetchEvents()` uses the circuit breaker on all outbound HTTP calls
- [ ] Sensitive config keys include the word `token`, `apiToken`, `secret`, `password`, or `apiKey` so they are auto-redacted in API responses
- [ ] `sourceId` is stable (same document always has the same ID)
- [ ] Unit tests cover the validate and fetch paths
- [ ] No credentials are hardcoded; all config comes from `ConnectorConfig.getConfig()`

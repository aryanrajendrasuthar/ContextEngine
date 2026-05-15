
#!/usr/bin/env python3
"""
Generates 500 realistic knowledge events covering GitHub PRs, Slack messages,
Jira tickets, and engineering decisions. Posts them to the ingestion-service
to seed the system for development and demos.
"""

import json
import random
import uuid
from datetime import datetime, timedelta, timezone

import httpx

INGESTION_URL = "http://localhost:8082/api/v1/events"
ORGANIZATION_ID = "org-demo-acme"

ENGINEERS = [
    {"id": "U001", "name": "Sarah Chen"},
    {"id": "U002", "name": "Marcus Williams"},
    {"id": "U003", "name": "Priya Patel"},
    {"id": "U004", "name": "Tom Nakamura"},
    {"id": "U005", "name": "Elena Vasquez"},
    {"id": "U006", "name": "James O'Brien"},
    {"id": "U007", "name": "Aisha Mohammed"},
    {"id": "U008", "name": "David Kim"},
]

GITHUB_REPOS = [
    "acme/payment-service",
    "acme/auth-service",
    "acme/user-service",
    "acme/api-gateway",
    "acme/notification-service",
    "acme/data-pipeline",
]

SLACK_CHANNELS = [
    "engineering",
    "architecture-decisions",
    "incidents",
    "backend",
    "platform",
    "on-call",
]

JIRA_PROJECTS = ["PLAT", "AUTH", "PAY", "INFRA", "DATA"]

GITHUB_PR_TEMPLATES = [
    ("Migrate {service} from REST to gRPC for internal communication",
     "This PR migrates the internal {service} API from REST/JSON to gRPC/Protobuf. "
     "The primary motivation is performance — we were seeing p99 latency of 180ms for "
     "internal service calls. After migration, p99 dropped to 12ms. Protobuf serialization "
     "is approximately 6x faster than JSON for our payload sizes. Backward compatibility "
     "is maintained through an adapter layer for external clients."),

    ("Add circuit breaker to {service} downstream calls",
     "Implements Resilience4j circuit breakers on all external calls from {service}. "
     "During the outage last week, a slow response from the downstream payment processor "
     "caused connection pool exhaustion that cascaded to the entire {service}. "
     "Circuit breakers open after 5 consecutive failures and reset after 30 seconds. "
     "Fallback behavior returns cached data where available or a graceful error response."),

    ("Refactor {service} authentication to use JWT claims",
     "Removes the database lookup per request that was happening to validate session tokens. "
     "JWT claims now carry the user ID, org ID, and permissions. "
     "This reduces {service} database load by approximately 40% and removes a synchronous "
     "dependency on the auth service for every authenticated request. "
     "Token expiry is set to 15 minutes with 30-day refresh tokens."),

    ("Replace sequential IDs with UUIDs in {service}",
     "Migrates the primary key strategy for {service} entities from PostgreSQL sequences "
     "to UUID v4. Sequential IDs expose internal record counts to clients and create "
     "coordination bottlenecks when we eventually shard. UUIDs eliminate both problems. "
     "Migration script backfills existing records. No downtime required."),

    ("Add distributed tracing to {service}",
     "Integrates OpenTelemetry distributed tracing throughout {service}. "
     "Every incoming request generates a trace ID that propagates through all "
     "downstream calls, database queries, and Kafka messages. "
     "Spans are exported to Jaeger for visualization. "
     "This resolves the issue where latency spikes were impossible to attribute "
     "to a specific database query or service call."),
]

SLACK_MESSAGE_TEMPLATES = [
    ("We made the call to {decision} for the {service} — {engineer} wrote up the ADR in {ticket}. "
     "Main reasons: {reason1} and {reason2}. Happy to answer questions."),

    ("Heads up: {service} will be undergoing maintenance this weekend for the {change}. "
     "Downtime window is Saturday 2-4am UTC. @{engineer} is the on-call contact."),

    ("Post-mortem for last week's {service} incident is posted in Confluence. "
     "Root cause was {root_cause}. We're adding {mitigation} to prevent recurrence."),

    ("Quick decision: we're going with {choice} over {alternative} for {purpose}. "
     "The licensing cost and operational complexity of {alternative} weren't worth it "
     "for our scale. Will revisit in Q3."),

    ("Architecture review notes from today: agreed to move forward with {approach} for {feature}. "
     "{engineer} will own the implementation, target is end of sprint."),
]

JIRA_TICKET_TEMPLATES = [
    ("{project}-{num}: Investigate latency regression in {service}",
     "p99 latency for {endpoint} increased from {before}ms to {after}ms after the deploy on {date}. "
     "Need to identify the root cause. Hypothesis: the new index on {table} is causing full "
     "table scans on write path due to query planner statistics not being updated. "
     "Action: run ANALYZE on {table} in staging and compare execution plans."),

    ("{project}-{num}: Design connection pooling strategy for {service} database layer",
     "Current configuration uses default HikariCP settings which are not tuned for our workload. "
     "Under load testing at 200 concurrent requests, we see connection timeout errors. "
     "Need to determine correct pool size based on database server max_connections, "
     "number of service instances, and expected concurrent request rate."),

    ("{project}-{num}: Implement rate limiting on {service} public API",
     "Without rate limiting, a single misbehaving client can exhaust resources and affect "
     "all other users. Implement per-API-key rate limiting using sliding window algorithm. "
     "Limits: 1000 requests/minute per key, 10000 requests/hour. "
     "Return 429 with Retry-After header when exceeded. Use Redis for limit state."),

    ("{project}-{num}: Migrate {service} schema to support multi-tenancy",
     "Upcoming enterprise customers require data isolation. "
     "Evaluate three approaches: (1) row-level security with tenant_id column, "
     "(2) schema-per-tenant, (3) database-per-tenant. "
     "Recommendation: row-level security for operational simplicity at current scale, "
     "with schema-per-tenant path documented for future migration if needed."),
]

DECISIONS = [
    ("use Kafka instead of RabbitMQ", "durability and replay capability", "operational maturity of the team"),
    ("adopt PostgreSQL row-level security", "enforcement at the database layer", "simplicity versus schema-per-tenant"),
    ("standardize on UUID v4 for all primary keys", "no sequential enumeration by clients", "cross-service ID generation without coordination"),
    ("use Redis for all session state", "stateless service instances", "horizontal scaling without sticky sessions"),
    ("adopt gRPC for service-to-service communication", "performance and strict schema contracts", "code generation from protobuf definitions"),
]


def random_past_date(days_back: int = 365) -> datetime:
    delta = timedelta(days=random.randint(0, days_back), hours=random.randint(0, 23))
    return datetime.now(timezone.utc) - delta


def build_github_event() -> dict:
    repo = random.choice(GITHUB_REPOS)
    service = repo.split("/")[1]
    author = random.choice(ENGINEERS)
    template = random.choice(GITHUB_PR_TEMPLATES)
    pr_num = random.randint(100, 2000)
    title, body = template
    ts = random_past_date()

    return {
        "sourceId": f"github-pr-{repo.replace('/', '-')}-{pr_num}",
        "sourceType": "GITHUB",
        "content": f"{title.format(service=service)}\n\n{body.format(service=service)}",
        "authorId": author["id"],
        "authorName": author["name"],
        "timestamp": ts.isoformat(),
        "url": f"https://github.com/{repo}/pull/{pr_num}",
        "metadata": {
            "organizationId": ORGANIZATION_ID,
            "repository": repo,
            "prNumber": str(pr_num),
            "state": random.choice(["merged", "merged", "merged", "closed"]),
        },
    }


def build_slack_event() -> dict:
    channel = random.choice(SLACK_CHANNELS)
    author = random.choice(ENGINEERS)
    service = random.choice(GITHUB_REPOS).split("/")[1]
    decision = random.choice(DECISIONS)
    ts = random_past_date()
    ts_slack = f"{int(ts.timestamp())}.{random.randint(100000, 999999)}"

    template = random.choice(SLACK_MESSAGE_TEMPLATES)
    content = template.format(
        decision=decision[0],
        service=service,
        engineer=random.choice(ENGINEERS)["name"],
        ticket=f"{random.choice(JIRA_PROJECTS)}-{random.randint(100, 999)}",
        reason1=decision[1],
        reason2=decision[2],
        change=f"{service} database migration",
        root_cause="connection pool exhaustion caused by a slow downstream dependency",
        mitigation="circuit breakers on all external HTTP calls",
        choice="Qdrant",
        alternative="Pinecone",
        purpose="vector search",
        approach="event-sourcing pattern",
        feature=f"{service} audit log",
    )

    return {
        "sourceId": f"slack-{channel}-{ts_slack}",
        "sourceType": "SLACK",
        "content": content,
        "authorId": author["id"],
        "authorName": author["name"],
        "timestamp": ts.isoformat(),
        "url": f"https://acmecorp.slack.com/archives/C{random.randint(10000000, 99999999)}/p{ts_slack.replace('.', '')}",
        "metadata": {
            "organizationId": ORGANIZATION_ID,
            "channel": channel,
            "channelId": f"C{random.randint(10000000, 99999999)}",
        },
    }


def build_jira_event() -> dict:
    project = random.choice(JIRA_PROJECTS)
    ticket_num = random.randint(100, 2000)
    author = random.choice(ENGINEERS)
    service = random.choice(GITHUB_REPOS).split("/")[1]
    template = random.choice(JIRA_TICKET_TEMPLATES)
    ts = random_past_date()

    title_template, body_template = template
    title = title_template.format(project=project, num=ticket_num, service=service)
    body = body_template.format(
        service=service,
        endpoint=f"/api/v1/{service}/items",
        before=random.randint(50, 150),
        after=random.randint(200, 800),
        date=(ts + timedelta(days=random.randint(-30, -1))).strftime("%Y-%m-%d"),
        table=f"{service.replace('-', '_')}_events",
        project=project,
        num=ticket_num,
    )

    return {
        "sourceId": f"jira-{project}-{ticket_num}",
        "sourceType": "JIRA",
        "content": f"{title}\n\n{body}",
        "authorId": author["id"],
        "authorName": author["name"],
        "timestamp": ts.isoformat(),
        "url": f"https://acmecorp.atlassian.net/browse/{project}-{ticket_num}",
        "metadata": {
            "organizationId": ORGANIZATION_ID,
            "project": project,
            "ticketKey": f"{project}-{ticket_num}",
            "status": random.choice(["Done", "Done", "In Progress", "Closed"]),
            "priority": random.choice(["Medium", "High", "Low", "High"]),
        },
    }


def generate_events(count: int = 500) -> list[dict]:
    events = []
    for _ in range(count):
        source_type = random.choices(
            ["github", "slack", "jira"],
            weights=[35, 40, 25],
            k=1,
        )[0]
        if source_type == "github":
            events.append(build_github_event())
        elif source_type == "slack":
            events.append(build_slack_event())
        else:
            events.append(build_jira_event())
    return events


def post_events(events: list[dict]) -> None:
    print(f"Posting {len(events)} knowledge events to {INGESTION_URL}")
    success_count = 0
    failure_count = 0

    with httpx.Client(timeout=30) as client:
        for i, event in enumerate(events):
            try:
                response = client.post(INGESTION_URL, json=event)
                if response.status_code in (200, 201, 202):
                    success_count += 1
                else:
                    failure_count += 1
                    print(f"  Event {i}: HTTP {response.status_code} — {response.text[:100]}")
            except httpx.ConnectError:
                print(f"  Cannot connect to {INGESTION_URL}. Is the ingestion-service running?")
                return
            except Exception as e:
                failure_count += 1
                print(f"  Event {i}: Error — {e}")

            if (i + 1) % 50 == 0:
                print(f"  Progress: {i + 1}/{len(events)} ({success_count} success, {failure_count} failed)")

    print(f"\nComplete: {success_count} succeeded, {failure_count} failed")


if __name__ == "__main__":
    events = generate_events(500)
    with open("/tmp/sample-events.json", "w") as f:
        json.dump(events, f, indent=2, default=str)
    print("Sample events written to /tmp/sample-events.json")
    post_events(events)


# Incident Response Runbook

This document describes how to diagnose and recover from the most common failure modes in ContextEngine. Each section identifies the symptoms, the probable cause, the diagnostic steps, and the recovery procedure.

---

## 1. Query endpoint returning 503 or no answer

**Symptoms:** Users report that `/api/v1/query` returns 503, or that every answer is "I don't have enough information."

**Probable causes:**
- Ollama is down or the LLM model has not been pulled
- Qdrant is unreachable (the query has no vectors to search)
- The Redis cache is down (queries cannot be served or cached)

**Diagnostic steps:**

```bash
# Check service health
curl http://localhost:8080/actuator/health
curl http://localhost:8084/actuator/health

# Check Ollama
curl http://localhost:11434/api/tags

# Check Qdrant
curl http://localhost:6333/collections

# Check Redis
redis-cli ping
```

**Recovery:**

For Ollama: ensure the container is running and the model is loaded.
```bash
docker compose up -d ollama
docker exec ollama ollama pull nomic-embed-text
docker exec ollama ollama pull llama3.1:8b
```

For Qdrant: restart the container. Qdrant persists its data to disk, so vectors are retained across restarts.
```bash
docker compose restart qdrant
```

For Redis: Redis uses an append-only log for persistence by default in our docker-compose configuration. Restarting it will replay the AOF on startup.
```bash
docker compose restart redis
```

---

## 2. Kafka consumer lag growing — new documents not appearing in search results

**Symptoms:** Recently ingested documents do not appear in query results. Grafana shows Kafka consumer lag for the `embedding-service` group is increasing.

**Probable causes:**
- The embedding service has crashed
- Ollama is not responding to embedding requests
- Qdrant is rejecting upserts (storage full or schema mismatch)

**Diagnostic steps:**

```bash
# Check embedding service logs
docker compose logs --tail=100 embedding-service

# Check Kafka consumer group lag
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group embedding-service

# Check Qdrant storage
curl http://localhost:6333/telemetry
```

**Recovery:**

If the embedding service crashed, restart it. The Kafka consumer group tracks its offset, so restarting will resume from the last committed message without data loss.
```bash
docker compose restart embedding-service
```

If Ollama is the bottleneck, the embedding service will retry automatically (3 attempts with exponential backoff). Ensure Ollama is running and the model is loaded.

If a message is permanently stuck (poison pill), it will be routed to the dead letter queue topic `contextengine.knowledge.errors` after retry exhaustion. The consumer will continue past it and commit the offset.

---

## 3. PostgreSQL connection pool exhausted

**Symptoms:** Services return 500 errors with "connection pool timed out" in logs. `spring.datasource.hikari.pending` shows a large backlog.

**Probable causes:**
- A long-running query is holding connections
- Too many service instances competing for the same pool
- Database is under heavy load or slow due to missing indexes

**Diagnostic steps:**

```bash
# Check active connections
docker exec postgresql psql -U contextengine -c "
  SELECT pid, state, wait_event_type, wait_event, query_start, query
  FROM pg_stat_activity
  WHERE state != 'idle'
  ORDER BY query_start;"

# Check pool metrics
curl http://localhost:8082/actuator/metrics/hikaricp.connections.active
curl http://localhost:8082/actuator/metrics/hikaricp.connections.pending
```

**Recovery:**

Terminate long-running queries that are blocking the pool:
```bash
docker exec postgresql psql -U contextengine -c "
  SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
  WHERE state = 'active'
    AND query_start < now() - interval '5 minutes'
    AND pid != pg_backend_pid();"
```

If the pool size is genuinely too small, increase `DB_POOL_MAX_SIZE` in the ConfigMap and roll out a restart.

---

## 4. Gateway returning 429 Too Many Requests unexpectedly

**Symptoms:** Legitimate API calls from your application are being rate-limited.

**Probable causes:**
- The `X-Organization-Id` header is missing from requests, causing all traffic to share the `anonymous` bucket
- The rate limits are set too low for the current usage pattern

**Diagnostic steps:**

```bash
# Check if the header is being sent
curl -v http://localhost:8080/api/v1/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question": "test"}' 2>&1 | grep "X-Organization-Id"
```

**Recovery:**

If the header is missing, fix the client to include `X-Organization-Id` in every request.

If the rate limits are genuinely too low, raise them via environment variables:
- `QUERY_RATE_LIMIT_RPS` (default: 10 req/s per org)
- `QUERY_RATE_LIMIT_BURST` (default: 20)
- `INGESTION_RATE_LIMIT_RPS` (default: 5 req/s per org)

Update the ConfigMap and restart the gateway pod.

---

## 5. Neo4j knowledge graph is empty or missing nodes

**Symptoms:** The People Graph page shows no nodes. Queries return answers but with no related concepts or people listed.

**Probable causes:**
- The knowledge-graph-service is not running or has crashed
- The `contextengine.knowledge.processed` topic has no messages (embedding pipeline backed up)
- Neo4j is unreachable

**Diagnostic steps:**

```bash
# Check knowledge-graph-service logs
docker compose logs --tail=50 knowledge-graph-service

# Verify Neo4j has data
docker exec neo4j cypher-shell -u neo4j -p contextengine_secret \
  "MATCH (n) RETURN labels(n), count(n) ORDER BY count(n) DESC"

# Check Kafka topic has messages
docker exec kafka kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic contextengine.knowledge.processed
```

**Recovery:**

If the service has crashed, restart it. Kafka retains messages for 7 days by default in our configuration, so the service will catch up on all missed events.

If Neo4j is empty despite events being processed, check the service logs for Cypher errors. A schema mismatch (e.g., a node property name change) would cause graph writes to fail silently.

---

## Escalation

If none of the above procedures resolve the incident, gather the following before escalating:

1. Output of `docker compose ps` and `docker compose logs --tail=200 <service-name>`
2. Prometheus metrics from the Grafana dashboard (screenshot or export)
3. The specific request that is failing (method, URL, headers, body)
4. The exact error response and any relevant correlation IDs from structured logs

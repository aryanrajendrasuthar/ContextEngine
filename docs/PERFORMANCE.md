
# ContextEngine Performance Baseline

This document describes the load testing methodology, the service-level objectives (SLOs) that ContextEngine is designed to meet, and a results template for recording baselines as the system evolves.

## Service-Level Objectives

These targets define the performance contract for a healthy deployment. Any regression beyond these thresholds should trigger investigation before the build is promoted.

| Metric | Target | Critical threshold |
|--------|--------|--------------------|
| Query p99 latency | < 3 000 ms | > 5 000 ms |
| Query p95 latency | < 1 500 ms | > 3 000 ms |
| Query p50 latency | < 800 ms | > 1 500 ms |
| Ingestion p95 latency | < 500 ms | > 1 000 ms |
| Error rate (both endpoints) | < 1% | > 5% |
| Ingestion throughput | ≥ 20 events/s sustained | < 10 events/s |

Query latency includes the full RAG pipeline: embedding the question, vector search in Qdrant, graph context retrieval from Neo4j, and LLM answer generation via Ollama. On CPU-only hardware, LLM generation time dominates. On hardware with a GPU, the LLM step drops from 5–15 seconds to under 500 ms and the SLO becomes easy to meet.

Cache hits bypass the LLM entirely and return in under 100 ms. The p50 target accounts for a realistic mix of cache hits and misses under sustained load.

## Test Suites

### Query Load Test — `tests/load/k6-query.js`

Simulates concurrent users asking natural language questions against a pre-populated knowledge base.

**Stages:**

| Stage | Duration | Virtual users |
|-------|----------|---------------|
| Ramp-up | 30 s | 0 → 10 VUs |
| Scale-up | 1 min | 10 → 50 VUs |
| Sustain | 2 min | 100 VUs |
| Ramp-down | 30 s | 100 → 0 VUs |

Total test duration: approximately 4 minutes.

**What it tests:** The RAG pipeline under concurrent load, including Redis cache behavior (the 8 question variants are designed so that repeated questions from different VUs hit the 1-hour cache). A healthy run at 100 VUs should show p50 dropping over time as the cache warms up.

**Prerequisites:**
1. A running full stack (`docker compose up`)
2. At least 20 documents ingested and processed (wait for the embedding pipeline to complete)
3. A valid auth token and organization ID

**Run command:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"load@test.local","password":"LoadTest123"}' | jq -r .accessToken)

ORG=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"load@test.local","password":"LoadTest123"}' | jq -r .organizationId)

k6 run \
  --env BASE_URL=http://localhost:8080 \
  --env AUTH_TOKEN=$TOKEN \
  --env ORG_ID=$ORG \
  tests/load/k6-query.js
```

Results are written to `tests/load/results/k6-query-results.json`.

### Ingestion Load Test — `tests/load/k6-ingestion.js`

Simulates concurrent clients submitting documents to the ingestion pipeline.

**Stages:**

| Stage | Duration | Virtual users |
|-------|----------|---------------|
| Ramp-up | 20 s | 0 → 5 VUs |
| Sustain | 1 min | 20 VUs |
| Ramp-down | 30 s | 20 → 0 VUs |

Total test duration: approximately 2 minutes.

**What it tests:** The ingestion endpoint's ability to accept and queue events under load. Each VU generates a unique `sourceId` to prevent the deduplication check from masking throughput — the test is measuring the intake path, not Kafka consumer throughput.

**Note on the threshold:** The p95 < 500 ms target is for the intake path only (validate, deduplicate check, publish to Kafka, return 201). The embedding pipeline processes events asynchronously; Kafka consumer lag is the relevant metric for downstream throughput, not this endpoint's latency.

## Environment Profiles

Results vary significantly across deployment environments. Record the environment alongside every baseline.

### Local (Docker Compose, CPU only)

This is the baseline all developers can reproduce. LLM generation is slow because Ollama runs on CPU.

Expected query results at 10 VUs (low concurrency, minimal cache warming):

| Metric | Expected range |
|--------|---------------|
| p50 | 4 000–12 000 ms |
| p95 | 10 000–25 000 ms |
| p99 | 15 000–30 000 ms |
| Error rate | < 1% |

At higher VU counts (50–100), the Ollama CPU bottleneck causes requests to queue and latency climbs above the 3-second SLO. This is expected on CPU-only hardware. The SLO is defined for GPU-accelerated or cloud deployment.

### Local (Docker Compose, with GPU)

With Ollama running on an Nvidia GPU via CUDA or Apple Silicon via Metal, LLM generation drops to under 500 ms for most prompts. Expected results at 50 VUs with a warm cache:

| Metric | Expected range |
|--------|---------------|
| p50 | 200–600 ms |
| p95 | 600–1 500 ms |
| p99 | 1 000–2 500 ms |
| Error rate | < 0.5% |

### Kubernetes (3-node cluster, GPU nodes)

In a production-like deployment with the query-service HPA engaged (2–5 replicas, 4 CPU, 4 Gi each) and Ollama on dedicated GPU nodes, the system is designed to sustain 100 concurrent users within SLO.

## Results Log

Record each baseline run here. This table is a living record — add rows as the system evolves.

| Date | Environment | VUs | p50 (ms) | p95 (ms) | p99 (ms) | Error rate | Notes |
|------|-------------|-----|----------|----------|----------|------------|-------|
| — | — | — | — | — | — | — | Initial baseline pending first full stack run |

To add a result: run the k6 test, read the summary from stdout or `k6-query-results.json`, and append a row to this table.

## Profiling Bottlenecks

When a load test result misses the SLO, use these indicators to isolate the bottleneck:

**If p99 is high but p50 is acceptable:** The bottleneck is likely LLM generation for cache-miss queries. The fast p50 means cached responses are fast; only slow (non-cached) queries drag up the tail. Solutions: increase cache TTL, pre-warm the cache with common questions, or add LLM inference capacity.

**If both p50 and p99 are high:** The system is saturated at a shared resource. Check Grafana for JVM heap pressure (query-service GC pauses), Qdrant query latency (under `vector_search_duration_ms` in the embedding service), and Neo4j query time. Redis connection exhaustion will also cause this pattern.

**If error rate climbs above 1%:** Check for 429s (rate limiter too restrictive for load test concurrency — adjust `burst` in gateway config for load tests), 503s (a service is crashing under load — check pod restart count in Kubernetes), or 500s (application bug triggered by concurrency — read service logs).

**If ingestion p95 is high:** The Kafka broker may be a bottleneck. Check `kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec` via Prometheus. If the ingestion endpoint itself is slow (before publishing), check PostgreSQL connection pool saturation.

## Running the Full Benchmark Suite

A complete benchmark run tests both ingestion and query in sequence. Ingest first to ensure the knowledge base is populated, then wait for the embedding pipeline to process the events before running the query test.

```bash
# 1. Ingest a large batch
k6 run --env BASE_URL=http://localhost:8080 \
       --env AUTH_TOKEN=$TOKEN \
       --env ORG_ID=$ORG \
       tests/load/k6-ingestion.js

# 2. Wait for the embedding pipeline to catch up
# Watch Kafka lag in Grafana or check the events/{sourceId} status endpoint
# until the most recently ingested events show status: PROCESSED

# 3. Run the query load test
k6 run --env BASE_URL=http://localhost:8080 \
       --env AUTH_TOKEN=$TOKEN \
       --env ORG_ID=$ORG \
       tests/load/k6-query.js
```

The results from step 3 are the canonical SLO validation. Results from step 1 validate ingestion throughput independently.

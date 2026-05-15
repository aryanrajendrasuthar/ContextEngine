
/**
 * k6 load test — Query service
 *
 * Tests the full RAG pipeline under concurrent load.
 * Target: p99 < 3 seconds at 100 concurrent users.
 *
 * Prerequisites:
 *   1. k6 installed: brew install k6
 *   2. Obtain an auth token and org ID before running:
 *      TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
 *        -H 'Content-Type: application/json' \
 *        -d '{"email":"load@test.local","password":"LoadTest123"}' | jq -r .accessToken)
 *      ORG=$(curl -s ... | jq -r .organizationId)
 *
 * Run:
 *   k6 run --env BASE_URL=http://localhost:8080 \
 *          --env AUTH_TOKEN=$TOKEN \
 *          --env ORG_ID=$ORG \
 *          tests/load/k6-query.js
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const errorRate = new Rate('errors')
const queryDuration = new Trend('query_duration_ms', true)

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // ramp up to 10 VUs
    { duration: '1m',  target: 50 },   // ramp up to 50 VUs
    { duration: '2m',  target: 100 },  // sustain 100 concurrent users
    { duration: '30s', target: 0 },    // ramp down
  ],
  thresholds: {
    // Core SLO: p99 query latency must be under 3 seconds
    http_req_duration: ['p(99)<3000'],
    // Less than 1% of requests should fail
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const AUTH_TOKEN = __ENV.AUTH_TOKEN
const ORG_ID = __ENV.ORG_ID

// Varied questions to exercise different cache paths
const QUESTIONS = [
  'How does ContextEngine handle database migrations?',
  'What vector database is used for semantic search?',
  'How are knowledge events deduplicated?',
  'What is the chunking strategy for documents?',
  'How does the knowledge graph relate to RAG answers?',
  'What happens when Ollama is unavailable?',
  'How are multi-tenant organizations isolated?',
  'What is the refresh token expiry time?',
]

export default function () {
  const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)]

  const start = Date.now()
  const resp = http.post(
    `${BASE_URL}/api/v1/query`,
    JSON.stringify({ question }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${AUTH_TOKEN}`,
        'X-Organization-Id': ORG_ID,
      },
      timeout: '30s',
    }
  )
  const duration = Date.now() - start
  queryDuration.add(duration)

  const ok = check(resp, {
    'status is 200': (r) => r.status === 200,
    'has answer field': (r) => {
      try {
        return JSON.parse(r.body).answer !== undefined
      } catch {
        return false
      }
    },
    'answer is non-empty': (r) => {
      try {
        return JSON.parse(r.body).answer.length > 0
      } catch {
        return false
      }
    },
  })

  errorRate.add(!ok)
  sleep(1)
}

export function handleSummary(data) {
  return {
    'tests/load/results/k6-query-results.json': JSON.stringify(data, null, 2),
    stdout: `
=== Query Load Test Results ===
p50 latency:  ${data.metrics.http_req_duration?.values?.['p(50)']?.toFixed(0)}ms
p95 latency:  ${data.metrics.http_req_duration?.values?.['p(95)']?.toFixed(0)}ms
p99 latency:  ${data.metrics.http_req_duration?.values?.['p(99)']?.toFixed(0)}ms
Error rate:   ${(data.metrics.errors?.values?.rate * 100)?.toFixed(2)}%
Total reqs:   ${data.metrics.http_reqs?.values?.count}
`,
  }
}

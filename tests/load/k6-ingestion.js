
/**
 * k6 load test — Ingestion pipeline
 *
 * Tests the ingestion endpoint under concurrent write load.
 * Each virtual user submits a unique document to prevent deduplication
 * from masking throughput issues.
 *
 * Run:
 *   k6 run --env BASE_URL=http://localhost:8080 \
 *          --env AUTH_TOKEN=$TOKEN \
 *          --env ORG_ID=$ORG \
 *          tests/load/k6-ingestion.js
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate } from 'k6/metrics'

const eventsIngested = new Counter('events_ingested')
const errorRate = new Rate('errors')

export const options = {
  stages: [
    { duration: '20s', target: 5 },
    { duration: '1m',  target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],   // ingestion endpoint should be fast (just accepts and queues)
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const AUTH_TOKEN = __ENV.AUTH_TOKEN
const ORG_ID = __ENV.ORG_ID

const SOURCE_TYPES = ['DOCUMENT', 'SLACK_MESSAGE', 'GITHUB_ISSUE', 'CONFLUENCE_PAGE']

export default function () {
  const sourceId = `load-test-${Date.now()}-${Math.random().toString(36).slice(2)}`
  const sourceType = SOURCE_TYPES[Math.floor(Math.random() * SOURCE_TYPES.length)]

  const payload = {
    sourceId,
    sourceType,
    content: `This is a load test document generated at ${new Date().toISOString()}. ` +
             `It discusses various topics including Kafka, Redis, PostgreSQL, and distributed systems. ` +
             `Source ID: ${sourceId}.`,
    metadata: {
      author: 'k6-load-test',
      url: `https://example.com/docs/${sourceId}`,
    },
  }

  const resp = http.post(
    `${BASE_URL}/api/v1/events/ingest`,
    JSON.stringify(payload),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${AUTH_TOKEN}`,
        'X-Organization-Id': ORG_ID,
      },
      timeout: '10s',
    }
  )

  const ok = check(resp, {
    'status is 201': (r) => r.status === 201,
  })

  if (ok) eventsIngested.add(1)
  errorRate.add(!ok)

  sleep(0.5)
}

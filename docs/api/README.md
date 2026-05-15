
# ContextEngine API Reference

All requests go through the API gateway at port 8080. Authenticated endpoints require:
- `Authorization: Bearer <access_token>`
- `X-Organization-Id: <organization_uuid>`

Errors follow RFC 7807 Problem Details format.

---

## Authentication — `/api/v1/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | None | Register a new user and organization. Returns access + refresh tokens. |
| POST | `/api/v1/auth/login` | None | Authenticate with email and password. Returns access + refresh tokens. |
| POST | `/api/v1/auth/refresh` | None | Exchange a refresh token (sent in `X-Refresh-Token` header) for a new access token. |
| POST | `/api/v1/auth/logout` | Bearer | Revoke the current refresh token. |

### Register

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "alice@acme.com",
  "password": "minimum8chars",
  "displayName": "Alice",
  "organizationName": "Acme Engineering"
}
```

**Response 201:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 2592000,
  "userId": "uuid",
  "email": "alice@acme.com",
  "displayName": "Alice",
  "organizationId": "uuid",
  "organizationName": "Acme Engineering",
  "role": "ADMIN"
}
```

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "alice@acme.com", "password": "minimum8chars" }
```

**Response 200:** Same shape as register response.

---

## Users — `/api/v1/users`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/users/me` | Bearer | Return the authenticated user's profile. |
| GET | `/api/v1/users/me/api-keys` | Bearer | List API keys (plain key not shown). |
| POST | `/api/v1/users/me/api-keys` | Bearer | Create an API key. Plain key shown once. |
| DELETE | `/api/v1/users/me/api-keys/{id}` | Bearer | Revoke an API key. |

### Create API Key

```http
POST /api/v1/users/me/api-keys
Authorization: Bearer <token>
X-Organization-Id: <org-id>
Content-Type: application/json

{ "name": "CI pipeline" }
```

**Response 201:**
```json
{
  "id": "uuid",
  "name": "CI pipeline",
  "keyPrefix": "ce_abcde",
  "plainKey": "ce_abcde...",
  "lastUsedAt": null,
  "expiresAt": null,
  "createdAt": "2025-01-01T00:00:00Z"
}
```

The `plainKey` field is only present in the creation response. Subsequent list or get calls return `null` for this field.

---

## Ingestion — `/api/v1/events`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/events/ingest` | Bearer | Submit a knowledge event for processing. |
| GET | `/api/v1/events/{sourceId}` | Bearer | Check processing status of an event. |

### Ingest Event

```http
POST /api/v1/events/ingest
Authorization: Bearer <token>
X-Organization-Id: <org-id>
Content-Type: application/json

{
  "sourceId": "doc-abc-123",
  "sourceType": "DOCUMENT",
  "content": "Full text content of the document...",
  "url": "https://wiki.acme.com/page/abc-123",
  "authorId": "user-uuid",
  "authorName": "Alice",
  "metadata": { "tags": ["architecture", "database"] }
}
```

**Response 201:** The created knowledge event record with `status: PENDING`.

Supported `sourceType` values: `DOCUMENT`, `SLACK_MESSAGE`, `GITHUB_ISSUE`, `GITHUB_PR`, `CONFLUENCE_PAGE`, `JIRA_ISSUE`, `NOTION_PAGE`.

---

## Connectors — `/api/v1/connectors`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/connectors` | Bearer | List all connectors for the organization. |
| GET | `/api/v1/connectors/{id}` | Bearer | Get a specific connector. |
| POST | `/api/v1/connectors` | Bearer | Create a new connector configuration. |
| PUT | `/api/v1/connectors/{id}` | Bearer | Update connector configuration. |
| POST | `/api/v1/connectors/{id}/activate` | Bearer | Enable polling for a connector. |
| POST | `/api/v1/connectors/{id}/deactivate` | Bearer | Pause polling. |
| POST | `/api/v1/connectors/{id}/test` | Bearer | Verify connectivity to the source system. |
| DELETE | `/api/v1/connectors/{id}` | Bearer | Delete a connector. |
| POST | `/api/v1/webhooks/{connectorId}` | None | Receive push events from the source system. |

---

## Query — `/api/v1/query`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/query` | Bearer | Ask a natural language question against the knowledge base. |

### Ask a Question

```http
POST /api/v1/query
Authorization: Bearer <token>
X-Organization-Id: <org-id>
Content-Type: application/json

{
  "question": "What is our database migration strategy?",
  "maxResults": 8
}
```

**Response 200:**
```json
{
  "answer": "ContextEngine uses Flyway for database schema migrations...",
  "sources": [
    {
      "sourceId": "doc-abc-123",
      "sourceType": "DOCUMENT",
      "url": "https://wiki.acme.com/page/abc-123",
      "title": null,
      "relevanceScore": 0.91
    }
  ],
  "confidence": 0.87,
  "relatedConcepts": ["flyway", "postgresql", "schema-migration"],
  "relatedPeople": ["Alice"],
  "cacheHit": false
}
```

`confidence` is the average relevance score of the top-3 retrieved chunks. Responses are cached per organization for 1 hour.

Rate limit: 10 requests/second per organization, burst up to 20. Returns HTTP 429 when exceeded.

---

## Graph — `/api/v1/graph`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/graph/people` | Header | Return all Person nodes and their concept connections. |
| GET | `/api/v1/graph/explore?query=<term>` | Header | Search concept/document/person nodes matching a keyword. |

Both endpoints require `X-Organization-Id` header (not Bearer token — auth handled at gateway level).

### Graph Data Response

```json
{
  "nodes": [
    { "id": "person:Alice", "label": "Alice", "type": "Person", "properties": { "docCount": 12 } },
    { "id": "concept:postgresql", "label": "postgresql", "type": "Concept", "properties": {} }
  ],
  "edges": [
    { "source": "person:Alice", "target": "concept:postgresql", "type": "KNOWS_ABOUT" }
  ]
}
```

Node types: `Person`, `Document`, `Concept`, `Decision`.

---

## Health and Observability

All services expose:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Service health (liveness + readiness) |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint |
| `GET /api-docs` | OpenAPI JSON schema |
| `GET /swagger-ui.html` | Interactive API documentation |

The embedding service exposes `GET /health` and `GET /metrics` (Prometheus format) at port 8086.

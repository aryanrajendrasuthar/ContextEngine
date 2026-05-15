
# Sprint 5: Frontend and User Management

**Duration:** Day 5, approximately 3 hours
**Status:** Planned

## Goal

Build the production-grade interface that makes ContextEngine usable for engineering teams — the query experience, knowledge explorer, and administrative dashboard.

## Deliverables

- user-service: JWT + refresh token authentication, organization management, team management, Keycloak SSO integration, API key management
- Frontend: React 18 + TypeScript + Tailwind CSS
- Pages: Login, Dashboard, Ask (primary query interface), Knowledge Explorer, Sources, People Graph, Admin Settings
- Ask Page: search input, markdown-rendered answer, source cards with title/author/date/URL, related concepts sidebar, query history
- Knowledge Explorer: browse indexed knowledge by source, date, author, or concept with full-text search
- Sources Page: view and manage connected sources with connection status, last sync time, document count
- People Graph: interactive D3 force-directed visualization of organizational knowledge connections
- Admin Settings: connector configuration, team management, usage analytics
- Real-time indexing status via Server-Sent Events
- LEARNING.md Sprint 5 section

## Commit Checkpoints

- CHECKPOINT 5A: user-service with JWT and Keycloak SSO
- CHECKPOINT 5B: Ask page and Knowledge Explorer
- CHECKPOINT 5C: People Graph, Sources page, Admin Settings, SSE

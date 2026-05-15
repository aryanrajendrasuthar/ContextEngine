
# Contributing to ContextEngine

This document defines the engineering workflow for contributing to ContextEngine. All contributors — regardless of seniority — follow these conventions. Consistent process reduces review friction and keeps the git history readable and navigable.

## Branch Strategy

ContextEngine uses a trunk-based development model with short-lived feature branches. The main branch (`main`) must always be in a deployable state. Direct commits to `main` are not permitted.

Branch names follow this pattern:

```
{type}/{short-description}
```

Examples:

```
feat/slack-connector
fix/kafka-consumer-offset-reset
chore/upgrade-spring-boot-3-2
docs/add-embedding-service-runbook
refactor/query-service-caching-layer
```

Valid types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `perf`

Keep branch lifetimes short. A branch that lives longer than two days should be broken into smaller pieces or merged as a work-in-progress with a feature flag. Long-lived branches create merge conflicts and make code review difficult.

## Commit Conventions

All commits must follow the Conventional Commits specification. This format enables automated changelog generation and makes `git log` readable at a glance.

Format:

```
{type}({scope}): {short description}

{optional body — explain why, not what}
```

The subject line must be under 72 characters. Use the imperative mood: "add connector framework" not "added connector framework". Do not end the subject with a period.

Examples:

```
feat(connector): add slack connector with channel message polling
fix(embedding): handle empty content field in knowledge event gracefully
perf(query): add redis cache for repeated organization queries
test(ingestion): add testcontainers integration test for kafka publishing
docs(architecture): update data flow diagram with neo4j enrichment step
chore(deps): upgrade qdrant-client to 1.8.0
```

Valid scopes: `gateway`, `ingestion`, `connector`, `embedding`, `query`, `knowledge-graph`, `user`, `frontend`, `infra`, `docs`, `deps`

## Pull Request Process

Every change enters `main` through a pull request, with no exceptions. The pull request description must include:

1. A summary of what changed and why (two to four sentences).
2. How to test the change locally (specific commands or steps).
3. Any schema migrations or infrastructure changes required.

A pull request must pass all of the following before merge:

- All unit tests pass (`mvn test`)
- All integration tests pass (`mvn verify`)
- No new compiler warnings introduced
- At least one reviewer approval from a team member who did not write the change

For changes that affect the Kafka schema, Qdrant collection schema, or PostgreSQL schema, an Architecture Decision Record update or new ADR may be required.

## Code Standards

**Java services.** All Java code targets Java 21. Use records for immutable data transfer objects. Prefer sealed interfaces over inheritance hierarchies where appropriate. All configuration values come from `application.yml` with environment variable overrides — no hardcoded strings for hosts, ports, or credentials. Use SLF4J for all logging; never use `System.out.println` in production code. Log at INFO for business events, DEBUG for detailed pipeline tracing, WARN for recoverable errors, and ERROR for failures that require attention.

**Python embedding service.** All Python code targets Python 3.11+. Use type annotations throughout. Handle all Kafka consumer exceptions with explicit logging and retry logic. The service must never crash on a malformed knowledge event — log the error, publish to the dead letter queue, and continue processing.

**Frontend.** All TypeScript, no `any` types. Components follow a functional pattern with hooks. API calls are isolated in the `src/api/` directory, not scattered across components. No inline styles — use Tailwind utility classes.

**Tests.** Unit tests cover all business logic. Integration tests using Testcontainers cover database interactions and Kafka publishing. No mocking of infrastructure — use real containers. A test that passes with mocked infrastructure is not evidence that the code works with real infrastructure.

## Environment Variables

Never commit secrets. Credentials for local development live in `.env` files that are listed in `.gitignore`. The `docker-compose.yml` provides sane defaults for local development. Production credentials are managed through Kubernetes Secrets and are never stored in the repository.

## Documentation

Public-facing API changes require OpenAPI annotation updates in the same pull request. Architecture changes that affect the system design or data flow require updates to the relevant document in `docs/architecture/`. Decisions that change the technology stack or a significant architectural pattern require a new ADR in `docs/decisions/`.

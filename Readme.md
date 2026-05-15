
# ContextEngine

ContextEngine is an enterprise knowledge intelligence platform. It continuously ingests organizational knowledge from GitHub, Slack, Jira, and Confluence, builds a semantic knowledge graph from that data, and enables any engineer or employee to ask natural language questions and receive answers grounded in actual company history — with every answer traced back to its source.

When someone asks "why did we move off synchronous HTTP between the auth service and billing?", ContextEngine retrieves the Slack thread, the architectural decision record, and the pull request that documented it — then synthesizes an answer citing all three.

## System Requirements

Minimum recommended specs to run this stack locally:

| Resource | Minimum | Recommended |
|---|---|---|
| RAM | 16 GB | 32 GB |
| CPU | 4 cores | 8 cores |
| Disk | 20 GB free | 40 GB free |
| OS | macOS, Linux, or Windows with WSL2 | macOS or Linux |

Ollama and all Docker containers combined will consume 10–14 GB of RAM. If available RAM is below 16 GB, set the Docker Desktop memory limit to 12 GB and do not run the full stack simultaneously with other memory-intensive applications.

## Architecture

ContextEngine uses a microservices architecture with event-driven ingestion. Seven services communicate through Kafka and direct HTTP.

```
Slack / GitHub / Jira
        |
  connector-service  ---POST---> ingestion-service ---Kafka---> embedding-service (Python)
                                        |                               |
                                   PostgreSQL                        Qdrant
                                                                       |
                              knowledge-graph-service <---Kafka--------+
                                        |
                                      Neo4j
                                        |
                              query-service <---> Redis (cache)
                                        |
                               API Gateway :8080
                                        |
                              React Frontend :3000
```

Core components:

- **API Gateway** — Spring Cloud Gateway. Single entry point, JWT validation, routing, rate limiting.
- **User Service** — Authentication, organizations, teams, Keycloak SSO, API key management.
- **Connector Service** — Plugin-based connectors for GitHub, Slack, Jira, Confluence, and webhooks.
- **Ingestion Service** — Event validation, deduplication, Kafka publishing.
- **Embedding Service** — Python FastAPI. Kafka consumer, chunking, Ollama embedding, Qdrant storage.
- **Knowledge Graph Service** — Kafka consumer, spaCy NER, Neo4j graph construction.
- **Query Service** — Full RAG pipeline: embed question, vector search, graph context, LLM answer, citations.

Data stores:

- **PostgreSQL** — Users, connectors, metadata, audit logs. Schema managed by Flyway.
- **Qdrant** — Vector embeddings for semantic search. One collection per organization.
- **Neo4j** — Knowledge graph: people, documents, concepts, decisions, and their relationships.
- **Redis** — Query response cache, rate limiting, distributed state.
- **Kafka** — Asynchronous event pipeline between services.
- **Ollama** — Local LLM inference. nomic-embed-text for embeddings, llama3.1:8b for query answering.

## Local Development Setup

### Prerequisites

- Docker Desktop 4.x or Docker Engine + Docker Compose v2
- Java 21 (for running services outside Docker)
- Maven 3.9+ (for building Java services)
- Node.js 20+ and npm 10+ (for the frontend)
- Python 3.11+ (for the embedding service outside Docker)

### Start the Infrastructure

```bash
docker compose up -d
```

This starts Kafka, Zookeeper, PostgreSQL, Qdrant, Neo4j, Redis, Prometheus, Grafana, and Ollama. The first startup takes several minutes as Docker pulls images and Ollama downloads model weights.

### Pull Ollama Models

On first run, pull the required models into Ollama:

```bash
docker exec ollama ollama pull nomic-embed-text
docker exec ollama ollama pull llama3.1:8b
docker exec ollama ollama pull mistral:7b
```

Model downloads are approximately 4–5 GB total and are cached in the `ollama_data` Docker volume. This only needs to be done once.

### Seed Sample Data

```bash
python scripts/generate-sample-data.py
```

### Build and Run Services

```bash
mvn clean package -DskipTests
cd frontend && npm install && npm run dev
```

## Service URLs

| Service | URL | Purpose |
|---|---|---|
| API Gateway | http://localhost:8080 | Primary entry point |
| User Service | http://localhost:8081 | Auth, users |
| Ingestion Service | http://localhost:8082 | Knowledge event intake |
| Connector Service | http://localhost:8083 | Source connectors |
| Query Service | http://localhost:8084 | Natural language queries |
| Knowledge Graph Service | http://localhost:8085 | Graph construction |
| Embedding Service | http://localhost:8086 | Embedding pipeline |
| Frontend | http://localhost:3000 | Web interface |
| Kafka UI | http://localhost:9090 | Topic inspection |
| Neo4j Browser | http://localhost:7474 | Graph visualization |
| Qdrant Dashboard | http://localhost:6333/dashboard | Vector store inspection |
| Grafana | http://localhost:3001 | Operational dashboards |
| Prometheus | http://localhost:9091 | Metrics |
| Keycloak | http://localhost:8180 | Identity provider (Sprint 5+) |

## API Documentation

OpenAPI/Swagger documentation is available at `http://localhost:{port}/swagger-ui.html` for each running service.

## Running Tests

```bash
mvn test          # Unit tests
mvn verify        # Integration tests (requires Docker for Testcontainers)
```

## Project Structure

```
contextengine/
├── docs/               Architecture documentation and ADRs
├── services/           All backend microservices
├── frontend/           React 18 + TypeScript + Tailwind CSS
├── infrastructure/     Docker, Kubernetes, scripts
├── scripts/            Data generators and operational scripts
├── LEARNING.md         Engineering education notes per sprint
└── docker-compose.yml  Full local development stack
```

## Contributing

See CONTRIBUTING.md for branch strategy, commit conventions, and code review standards.

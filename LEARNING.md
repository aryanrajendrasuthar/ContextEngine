
# ContextEngine Engineering Learning Notes

This file documents what you are learning at each sprint. It is written for engineers who have basic programming knowledge but have not yet worked in a production engineering environment. Each section explains not just what we built, but why it is built that way and what you would encounter doing this work at a real company.

---

## Sprint 1: Foundation, Architecture, and Infrastructure

### What is Retrieval-Augmented Generation (RAG) and why it matters

Language models are trained on text from the internet up to a cutoff date. They know nothing about your company's specific systems, decisions, or history. When you ask a general LLM "why did we switch from Postgres sequences to UUIDs for user IDs?", it will either say it doesn't know or — worse — generate a plausible-sounding but completely fabricated answer. This is called hallucination, and it is the central failure mode that makes raw LLMs untrustworthy for enterprise knowledge tasks.

RAG is the solution. The core idea is simple: before asking the LLM to answer, you first retrieve the relevant documents from your own knowledge base. You then give those documents to the LLM as context and ask it to answer only using what you've provided. The LLM becomes a reader and synthesizer rather than a guesser.

The pipeline looks like this: user asks a question → you convert the question to a vector embedding → you search your vector database for similar document chunks → you put those chunks in the LLM prompt → LLM generates a grounded answer with citations.

The power of this approach is that it combines the LLM's language comprehension ability (understanding nuance, summarizing, comparing) with your organization's actual knowledge (the ground truth). Neither alone is sufficient. The LLM alone hallucinates. The raw documents alone are unsearchable. Together, they give you accurate, cited, natural language answers.

### What is a vector database and how semantic search works

Traditional databases store text and let you search by exact keyword matching. If you store "We decided to use UUID for user identifiers" and someone searches for "sequential ID strategy", they get no results because the keywords don't match — even though the concepts are directly related.

Semantic search works differently. It converts text into a vector — a list of hundreds or thousands of floating point numbers — where the numbers represent the *meaning* of the text in a high-dimensional space. Documents with similar meaning have vectors that are mathematically close together, regardless of whether they share any words.

An embedding model (in our case, Ollama running nomic-embed-text) takes a sentence and outputs a 768-dimensional vector. The model has been trained on massive amounts of text to produce vectors where semantically similar concepts cluster together. "UUID", "universally unique identifier", and "database primary key strategy" will all produce vectors that are near each other in this space, even though the words are completely different.

A vector database is built specifically to answer the question "which stored vectors are most similar to this query vector?" It uses an approximate nearest neighbor algorithm called HNSW (Hierarchical Navigable Small World) to find the closest matches in logarithmic time — returning results from hundreds of thousands of vectors in under 50ms.

This is why ContextEngine can answer the question about UUIDs even if no stored document uses the exact phrase "UUID strategy for user IDs". The semantic similarity between the query and the relevant documents is captured in the vector space.

### What is a knowledge graph and how it differs from a relational database

A relational database stores data in tables. Relationships between tables are expressed through foreign keys and resolved at query time through joins. This works well when your data has a uniform, predictable structure.

A knowledge graph stores data as nodes (things) and edges (relationships between things). In Neo4j, a node might represent a person, a Slack message, a technical concept, or an architectural decision. An edge connects them: "Jane Smith AUTHORED this Slack message", "this Slack message MENTIONS the auth service", "the auth service REFERENCES this architectural decision".

The critical difference becomes apparent when you ask questions that require traversal. "Find all people who know about our rate limiting implementation" requires: start from the concept "rate limiting" → find all documents that mention it → find all people who authored those documents. In a relational database, this is three successive joins with increasing result set sizes — slow and complex. In Neo4j, it is a single Cypher pattern match that follows direct pointer references stored alongside each node, completing in milliseconds regardless of how many hops deep you go.

Knowledge graphs are the right data structure for modeling "who knows what about what" because organizations are inherently graph-shaped. Knowledge is not a table; it is a network of people, documents, and concepts connected by relationships.

### What is Neo4j and the Cypher query language

Neo4j is the most widely deployed graph database. It stores graphs natively — each node holds direct pointers to its adjacent edges, so traversal is a pointer-following operation rather than an index lookup. This is the "index-free adjacency" property that gives graph databases their traversal performance advantage over relational databases.

Cypher is Neo4j's query language. Its syntax uses ASCII-art patterns to represent graph shapes. A node is `(n)`, a relationship is `-[r]->`, and a label is `:Person`. You read Cypher queries left to right as prose:

```cypher
MATCH (p:Person)-[:AUTHORED]->(d:Document)-[:MENTIONS]->(c:Concept {name: "rate limiting"})
RETURN p.name, count(d) as documentCount
ORDER BY documentCount DESC
```

This reads: "Find all Persons who authored Documents that mention the concept named 'rate limiting', and return their names along with how many such documents they authored, sorted by most documents first."

Compare this to the equivalent SQL, which requires three tables, two joins, a GROUP BY, and careful index management to be performant. The Cypher version is not just shorter — it expresses the intent more directly and is easier to modify when the question changes.

### What is an embedding and why text must be converted to vectors

Text is not natively comparable. The string "UUID" and the string "universally unique identifier" share zero characters in common, so any algorithm based on character or word matching will say they are completely dissimilar. Humans understand they mean the same thing because we have a mental model of meaning — an internal representation that maps words to concepts.

An embedding model creates a machine-equivalent of that mental model. It maps words, sentences, and paragraphs to positions in a high-dimensional vector space where position encodes meaning. Concepts that humans consider similar are mapped to nearby positions; concepts humans consider different are mapped to distant positions. The model learned this mapping by processing billions of human-written documents and finding statistical patterns in how words and concepts co-occur.

When we say nomic-embed-text produces a 768-dimensional vector, we mean it produces a list of 768 numbers for any input text. Each number captures some aspect of the text's meaning — no single number has an obvious human interpretation, but together they encode the meaning precisely enough that cosine similarity between two vectors strongly predicts whether two texts are about the same topic.

The embedding step is what makes the entire RAG system work. Without it, we have keyword search. With it, we have semantic search — the ability to find relevant knowledge even when the question and the answer use entirely different words.

### How large companies like Notion, Confluence, and Glean approach knowledge management and where they fall short

Notion and Confluence are document editors with search built on top. Their search is primarily keyword-based, enhanced by metadata like tags and page titles. They solve the storage and organization problem but not the retrieval problem. An engineer who doesn't know the right document exists, or doesn't remember the keywords used in it, cannot find it.

Glean is a commercial search product that uses semantic search across connected company tools. It is the closest analog to ContextEngine and validates the market need. Its limitation for engineering teams is that it is a black box — you cannot control the retrieval logic, cannot tune it for technical terminology, cannot build custom views on top of the knowledge graph, and cannot integrate it with custom internal tools. It also requires trusting a third-party vendor with all your company's internal knowledge.

ContextEngine's differentiation is ownership and extensibility. The knowledge graph is queryable programmatically. The RAG pipeline can be tuned. New connectors can be added without a vendor contract. For companies that treat their engineering knowledge as a competitive asset, owning the infrastructure matters.

### What is a microservices architecture and why ContextEngine uses it

A monolith is a single deployable unit where all the code runs in one process. It is simple to develop initially — no network calls between components, shared memory, simple debugging. Most successful software starts as a monolith.

A microservices architecture splits the application into multiple independent services, each with a single responsibility, running as separate processes communicating over the network.

ContextEngine uses microservices for specific, justified reasons:

**Independent scaling.** The embedding pipeline (generating vectors via Ollama) is the throughput bottleneck. It can be scaled out to multiple instances without affecting the query service or the connector service. In a monolith, scaling means copying the entire application, which wastes resources.

**Independent deployment.** Updating the connector service does not require restarting the query service. In a knowledge management system where uptime matters — teams rely on it for answers throughout the workday — minimizing the blast radius of deployments is important.

**Technology heterogeneity.** The embedding service is Python because the NLP ecosystem (spaCy, transformers, Ollama clients) is richest in Python. The Java services use Spring Boot because the enterprise Java ecosystem for web services, Kafka integration, and database ORM is mature and production-tested. A monolith would force one language.

The cost is operational complexity — more services to deploy, more network calls to trace, more places for things to fail. This is why Kafka's durability and Resilience4j's circuit breakers are not optional in this architecture; they are the mechanisms that manage that complexity.

### What is Docker Compose and how it works

Docker packages applications and all their dependencies into a standardized unit called a container. A container includes the application binary, all library dependencies, and a minimal operating system layer. It runs identically on any machine with Docker installed — eliminating "it works on my machine" problems.

Docker Compose is a tool for running multiple containers together as a system. The `docker-compose.yml` file declares each container (as a service), its configuration, its port mappings, its environment variables, and its dependencies on other services. Running `docker compose up -d` starts all declared services in the correct order.

For ContextEngine, Docker Compose starts nine infrastructure components: Kafka, Zookeeper, PostgreSQL, Qdrant, Neo4j, Redis, Prometheus, Grafana, and Ollama. Without Docker Compose, setting up this stack manually would take hours and would work slightly differently on every developer's machine. With Docker Compose, it takes one command.

The `healthcheck` blocks in our `docker-compose.yml` tell Docker how to determine if a service is actually ready to accept connections. This matters because a container starting is not the same as a service being ready — PostgreSQL needs several seconds to initialize before it accepts connections. Health checks allow downstream services to wait for their dependencies to be truly ready rather than just started.

### What is an Architecture Decision Record (ADR) and why engineering teams write them

An Architecture Decision Record is a document that captures a significant technical decision: what was decided, why it was decided that way, what alternatives were considered and why they were rejected, and what consequences the decision has.

The critical insight behind ADRs is that the hardest part of understanding a codebase is not understanding what the code does — it is understanding why it was built that way. Code documents what. ADRs document why.

Without ADRs, the reasoning behind decisions lives in the heads of the engineers who made them. When those engineers leave, the reasoning leaves with them. The team is left with choices they cannot explain, which makes them afraid to change things (they might break something for reasons they don't understand) and unable to defend those choices to new engineers or leadership.

Writing ADRs forces clarity. You cannot write "we chose Qdrant because it was better" — you have to explain what "better" means in the context of your specific requirements, and why the alternatives did not satisfy those requirements. This thinking process often reveals assumptions that should be questioned.

### What a senior engineer actually does on Day 1 of a new project

A common misconception among new engineers is that Day 1 means writing code. In practice, Day 1 of a significant project at a real company is almost entirely documentation and architecture.

Before a line of production code is written, a senior engineer establishes: what problem are we actually solving (and is it the right problem), who are the users and what do they need, what are the non-negotiable constraints (cost, latency, scale), what technical choices will we make and why, and what will success look like.

This work happens in system design documents (like `docs/architecture/system-design.md`), architecture decision records (the ADR files), and sprint plans that break the work into pieces that can be estimated and tracked.

The documentation serves multiple purposes: it aligns the team on the approach before anyone invests weeks building in the wrong direction; it creates a record that new team members can read to understand context; and it forces the architect to think through details that would otherwise be discovered as bugs in production.

This is exactly what Sprint 1 of ContextEngine represents. The code written in Sprint 1 is all skeleton code — runnable but empty. The real deliverable is the shared understanding of what is being built and why, recorded in files that will outlast the engineers who wrote them.

---

## Sprint 2: Ingestion Pipeline and Connector Framework

### What the ingestion service actually does and why it exists

Every piece of organizational knowledge — a GitHub pull request, a Slack message, a Jira ticket — enters the system through the ingestion service. Its job is narrow and specific: receive an event, verify it has not been seen before, persist it, and publish it to Kafka so downstream services can process it asynchronously.

This narrow focus is deliberate. The ingestion service knows nothing about embeddings, knowledge graphs, or answers. It only ensures that every event is validated, deduplicated, persisted, and forwarded exactly once. Each of those responsibilities is a meaningful engineering problem on its own.

Separation of concerns is not just an academic principle. In practice it means that if the embedding service is slow or offline, events pile up in Kafka rather than being lost. If the ingestion service crashes and restarts, it picks up where it left off without data loss. If you need to replay events through the embedding pipeline, you replay from Kafka without touching the ingestion service. Each component can be scaled, debugged, and deployed independently.

### Content deduplication: why you need two layers

The ingestion service implements deduplication at two independent layers: Redis and PostgreSQL.

The Redis layer is fast. It keeps a SHA-256 hash of each ingested event's content in memory with a 24-hour TTL. When a new event arrives, the service computes its hash and checks Redis before touching the database. If the hash is found, the event is rejected immediately — the database is not consulted at all. For a system that might receive thousands of events per minute from connectors that retry on failure, this in-memory check is what keeps the database from being overwhelmed with duplicate lookups.

The PostgreSQL layer is authoritative. It stores a unique index on `(organization_id, source_id)`, which is the unique identifier of an event within its source system (a pull request number, a Jira ticket key, a Slack message timestamp). This check handles the case where the Redis cache has expired but the event was processed long ago. It is also the source of truth if Redis is restarted and loses its in-memory state.

This is a standard pattern in high-throughput systems: fast approximate filter in front of a slower authoritative store. Neither layer alone is sufficient. Redis alone loses state on restart. PostgreSQL alone is too slow under load. Together, the system is both fast and correct.

The SHA-256 hash is computed from the event's content string, not its identifier. This detects cases where the same source event is re-submitted with a different identifier but identical content — something that happens in practice with connectors that generate new IDs on retry.

### What Kafka provides that a direct HTTP call cannot

When the ingestion service accepts an event, it needs to notify downstream services — the embedding service, the knowledge graph service — that new work is available. The naive approach is a direct HTTP call: when an event arrives, immediately POST it to the embedding service. This fails in production for three reasons.

First, tight coupling. If the embedding service is down or overloaded, the ingestion service's request fails. You either drop the event (data loss) or block and retry (backpressure that can cascade into the ingestion service becoming unavailable too).

Second, no replay. If you need to re-embed all events after upgrading the embedding model, you have no way to replay them. The events were consumed and are gone.

Third, no ordering guarantees. You cannot ensure that events for the same organization are processed in sequence, which matters for correctness when building incremental graph structures.

Kafka solves all three. The ingestion service publishes an event to a Kafka topic and immediately returns — it does not wait for downstream processing. Kafka stores the event durably on disk and retains it for a configurable period. Downstream consumers read at their own pace, restart from any point in the log, and process events in partition order. The partition key (organization ID) ensures that all events for a given organization land on the same partition, preserving ordering.

At ContextEngine, an event published to Kafka gets embedded, stored in Qdrant, and represented in the Neo4j knowledge graph — three entirely independent processes, all driven by the same immutable log entry. Adding a fourth downstream processor in the future requires only a new Kafka consumer, with no changes to the ingestion service or the existing processors.

### What idempotency means and why it matters for distributed systems

Idempotency means: performing an operation multiple times produces the same result as performing it once. An idempotent HTTP endpoint is one where making the same request twice is safe — the second request is a no-op rather than creating a duplicate.

In distributed systems, you cannot assume that a request was received exactly once. Networks drop packets. Services restart mid-request. Load balancers time out and retry. The practical reality is that any operation may be attempted two, three, or four times by the time the originating caller gives up or receives a success response.

The ingestion service handles this correctly. If the same event is submitted twice — same `sourceId` and same content — the second submission returns HTTP 200 with a `DUPLICATE` result. No second record is created, no second Kafka message is published. The service is safe to retry without consequences.

This is why the deduplication logic is part of the ingestion service rather than being the caller's responsibility. You cannot trust that every caller will deduplicate correctly. The service must protect its own invariants.

Kafka producers in this system are also configured as idempotent producers (the `enable.idempotence=true` setting). This means Kafka guarantees that even if the producer retries a message delivery due to a network timeout, the broker will store the message exactly once. Without this setting, a producer retry would create a duplicate message in the topic.

### The plugin architecture for connectors

The connector service needs to integrate with multiple source systems — GitHub, Slack, Jira, webhooks, and more — each with a completely different API. A naive approach would be a large switch statement that hard-codes the integration logic for each source type.

The plugin architecture inverts this. A `ConnectorInterface` defines the contract: `fetchEvents(config, since)` and `testConnection(config)`. Every source system gets its own class that implements this interface. The `ConnectorRegistry` uses Spring's dependency injection to automatically collect all classes that implement `ConnectorInterface` and register them in a map keyed by connector type.

```
ConnectorRegistry
  │
  ├── GitHubConnector  (GITHUB)
  ├── SlackConnector   (SLACK)
  ├── JiraConnector    (JIRA)
  └── WebhookConnector (WEBHOOK)
```

Adding a new connector type — say, Confluence — requires creating exactly one new Java class that implements `ConnectorInterface` and annotating it with `@Component`. The registry picks it up automatically. No configuration changes, no switch statements, no modifications to existing code.

This is the Open/Closed Principle in practice: the system is open for extension (new connector types) but closed for modification (no existing code changes when you add one). It is also why Spring's IoC container is so powerful in real systems — it turns adding a feature from a modification task into a creation task.

### How the scheduler drives periodic synchronization

The `ConnectorScheduler` runs on a fixed delay (default: every 60 seconds). It queries the database for all connectors in `ACTIVE` status and calls `fetchEvents` on each one, passing the timestamp of the last successful sync as the `since` parameter. This means each sync only retrieves events created or updated since the previous run — not the entire history.

The pattern `config.getLastSyncAt() != null ? config.getLastSyncAt() : Instant.now().minus(30, DAYS)` is an example of a sensible default. A connector that has never run before needs a starting point. Defaulting to 30 days back is a practical choice: it captures recent history without overwhelming the pipeline on first run.

After a successful sync, the connector's status is set to `ACTIVE`, `lastSyncAt` is updated to the current time, and `documentsIndexed` is incremented by the number of events forwarded. If the sync fails — whether due to a network error, a rate limit, or an unexpected exception — the connector is marked `ERROR` and the failure message is stored. The scheduler will skip `ERROR` connectors on subsequent runs unless an operator reactivates them.

This approach means a single failing connector cannot crash the scheduler. Each connector's sync is wrapped in a try-catch that logs the error, marks the connector, and moves on to the next one.

### How the circuit breaker protects the system

When the connector-service calls the ingestion-service via HTTP, it does so through a Resilience4j circuit breaker. A circuit breaker is a pattern borrowed from electrical engineering: when too many failures occur in a short period, the circuit "opens" and subsequent calls fail immediately without even attempting the HTTP request. After a configurable wait period, the circuit enters a "half-open" state and allows a few test requests through. If those succeed, the circuit closes and normal operation resumes.

Without a circuit breaker, a slow or unavailable ingestion-service causes connector sync threads to pile up waiting for HTTP timeouts. Each thread holds a database connection, a memory allocation, and a scheduled task slot. Enough of them, and the connector-service runs out of resources and goes offline too. One failing downstream service cascades into total system failure — this is called a cascade failure, and it is one of the most common causes of production outages.

With the circuit breaker, after five failures in a ten-request window, the connector-service stops trying to reach the ingestion-service for 30 seconds. Events are dropped during the open period (logged, not silently discarded), but the connector-service stays healthy. When the ingestion-service recovers, the circuit closes automatically and normal operation resumes with no human intervention required.

### Webhooks: why push beats pull for real-time integrations

The scheduler-based connectors (GitHub, Slack, Jira) work by polling: every 60 seconds, ask the source system "what has changed since I last checked?" This is simple and reliable but has two drawbacks: latency (up to 60 seconds before a new event is ingested) and unnecessary API calls (60 calls per hour even when nothing has changed).

Webhooks invert the model. Instead of ContextEngine asking the source system for changes, the source system calls ContextEngine when changes occur. GitHub can be configured to send an HTTP POST to ContextEngine's `/api/v1/webhooks/{connectorId}` endpoint within seconds of a pull request being merged.

The tradeoff is that webhooks require the receiving service to be publicly accessible (a source system cannot POST to `localhost`) and require the source system to support webhooks (not all do). Polling works everywhere, even in isolated environments with no public endpoint.

In production, both models coexist: polling for systems that don't support webhooks, and webhooks for systems that do, with polling as a fallback or catch-up mechanism. The connector architecture supports both through the same `ConnectorInterface` — a webhook connector's `fetchEvents` simply returns an empty list, because events arrive through the HTTP endpoint instead.

### What tests are actually testing

Sprint 2 introduced the first real test suites. It is worth being explicit about what each type of test is checking.

Unit tests (like `IngestionServiceTest`) test a single class in complete isolation. Every dependency is replaced with a mock — a fake object that you control. The test asks: "given that my dependencies behave in a specific way, does this class behave correctly?" Unit tests run in milliseconds, have no external dependencies (no database, no network), and tell you precisely which class is broken when they fail.

Controller slice tests (like `IngestionControllerIntegrationTest` using `@WebMvcTest`) test the HTTP layer of the application: routing, request deserialization, response serialization, and validation annotations. Spring boots only the web layer, not the full application context. The service layer is mocked. These tests verify that HTTP contracts — status codes, request body validation, JSON field names — are correct.

The critical insight about mocking is that mocks do not test integration — they test behavior under controlled conditions. The test `kafkaFailure_marksEntityAsFailed` would be impossible to write reliably with a real Kafka broker because you would need to somehow force a broker failure at the right moment. With a mock, you simply tell the mock Kafka service to return a failed CompletableFuture, and the test verifies that the entity is marked FAILED in response.

Full integration tests (using Testcontainers) spin up real Docker containers for PostgreSQL and Kafka. These are the tests that catch configuration errors, Flyway migration problems, and query bugs that mocks cannot detect. They are slower (seconds, not milliseconds) and are run less frequently — typically in CI, not on every local code change.

The combination of all three test types gives you confidence at different levels: unit tests that your logic is correct, slice tests that your HTTP API is correct, and integration tests that the whole stack wires together correctly in a real environment.

---

## Sprint 3: Embedding Pipeline and Vector Search

### Why raw text is not searchable and what embeddings fix

The ingestion service stores events in PostgreSQL as raw text. You could search that text using PostgreSQL's `ILIKE` operator or full-text search. The problem is that these approaches only find documents that share the exact same words as the query.

A developer asks: "How do we handle database connection exhaustion?" The relevant document says: "When all HikariCP pool slots are occupied, new requests queue and eventually timeout. Use PgBouncer in transaction mode to multiplex connections." There is no word overlap between the query and the document — "database connection exhaustion" does not appear literally in the document. A keyword search returns nothing. A semantic search returns the document immediately.

Embeddings solve this by converting text into a point in a mathematical space where meaning determines proximity. When you embed "database connection exhaustion" and "HikariCP pool slots occupied", the two vectors end up close together because they were trained on text where these concepts appear in related contexts. Cosine similarity — the angle between the two vectors — is close to zero, meaning the documents are semantically similar.

The embedding model (nomic-embed-text, running locally via Ollama) converts any text string to a 768-dimensional vector. A 768-dimensional vector is a list of 768 floating point numbers, each between -1 and 1. The model has 137 million parameters trained to produce vectors where this semantic proximity property holds. You do not need to understand the model internals to use it — you only need to know that it maps semantically similar text to nearby vectors.

### What chunking is and why it matters

A language model has a maximum input length — a context window. nomic-embed-text accepts at most 8,192 tokens per call. A long Slack thread or detailed GitHub PR description might be 4,000 words, which is well within the limit. But the embedding of a 4,000-word document captures the average meaning of the entire document, which may be too diffuse to match a specific question about one detail in that document.

Chunking splits long documents into smaller overlapping segments — in ContextEngine, 512 tokens per chunk with 50-token overlap. Each chunk gets its own embedding, which represents a focused portion of the original document.

The 50-token overlap is critical. Without overlap, a sentence that is split across a chunk boundary would be half-embedded in each adjacent chunk. Neither chunk's embedding would capture the complete thought. With 50-token overlap, the end of chunk N appears again at the beginning of chunk N+1, so every sentence is fully represented in at least one chunk.

Chunk size is a tunable parameter. Smaller chunks (128 tokens) are more precise but lose context — a chunk containing only "it was rejected" with no surrounding explanation is useless. Larger chunks (1024 tokens) preserve more context but produce broader embeddings that match less specifically. 512 tokens is a widely-used default that balances precision and context.

The tiktoken library provides accurate token counting using the same tokenization algorithm as the embedding model. This matters because a "token" is not a word — it is a subword unit. "PostgreSQL" might be one or two tokens depending on the tokenizer. Splitting on word count would produce inconsistently sized chunks; splitting on token count produces chunks that respect the model's actual input units.

### How Qdrant stores and retrieves embeddings

Qdrant is a purpose-built vector database. It stores each embedding as a "point" — a vector plus an arbitrary JSON payload. The payload is stored alongside the vector and returned in search results, so you do not need to make a second database query to get the full document after finding a match.

ContextEngine uses one Qdrant collection per organization. This enforces tenant isolation at the data layer: a search against `org_acme` can never return results from `org_beta`, regardless of how similar their embeddings are. Creating one collection per organization also allows the vector index to remain small — HNSW performance degrades gracefully with size but benefits from smaller graphs — and allows collections to be deleted cleanly when an organization offboards.

Each point's ID is a deterministic UUID5 computed from the source event ID and chunk index. This makes upserts idempotent: if the same event is processed twice (due to a consumer restart or a Kafka redelivery), the second run produces the same point IDs and overwrites the existing points rather than creating duplicates. This is the vector store equivalent of the deduplication strategy in the ingestion service.

The HNSW index (Hierarchical Navigable Small World) is a graph-based approximate nearest neighbor algorithm. It builds a multi-layer graph where each node is connected to its nearest neighbors at different scales. A query traverses this graph, starting from a broad view and narrowing down to the nearest vectors. This finds the closest matches in O(log n) time, making searches over millions of vectors feasible at low latency.

### The role of the dead letter queue

In a processing pipeline, some events will always fail. The embedding model might be temporarily unavailable. A document might contain content that causes the embedding API to return an error. The Qdrant write might fail due to a network partition.

A naive consumer would retry indefinitely, blocking the pipeline. An equally naive consumer would skip failures silently, losing data.

The dead letter queue (DLQ) is the correct solution. When an event fails processing after retries, it is published to `contextengine.knowledge.errors` with a structured record explaining why it failed: the source ID, the failure reason, the timestamp, and a preview of the content. The consumer then commits the Kafka offset and moves on. The pipeline does not stall.

The DLQ serves two purposes. First, it is an audit trail: you can query the errors topic to understand what failed, when, and why, without the failures being silently dropped. Second, it is a reprocessing queue: if the failure was due to a temporary outage (Ollama was restarting), an operator can replay the DLQ events once the system is healthy.

The key insight is that not all errors are equal. A malformed event (unparseable JSON, missing required fields) will never succeed no matter how many times it is retried — commit immediately and move on. A transient network error (Ollama timed out) might succeed on retry — try up to three times with exponential backoff before giving up and sending to the DLQ. The tenacity retry library implements this distinction: `retry_if_exception_type` specifies which exception types trigger a retry, and everything else fails immediately.

### Why the consumer is synchronous in an async service

The embedding service is built on FastAPI, which is an async framework. FastAPI handles HTTP requests in an asyncio event loop. However, kafka-python — the Kafka client library used here — is synchronous. Its consumer poll loop is a blocking call.

Mixing synchronous blocking calls with asyncio is dangerous. If you call a blocking function from an async function without special handling, you block the entire event loop — all HTTP requests, all health checks, everything freezes until the blocking call returns.

The solution is to run the Kafka consumer in a separate operating system thread, completely outside the asyncio event loop. The consumer thread blocks freely in its poll loop, while FastAPI continues handling HTTP requests in the event loop thread. The two threads share no mutable state — the consumer thread only reads from Kafka and writes to Qdrant and back to Kafka.

This architecture pattern (async web layer + sync worker thread) is common in Python services that need to mix synchronous and asynchronous work. An alternative would be to use an async Kafka client (aiokafka) and run everything in the event loop, but that adds complexity and the synchronous approach is perfectly adequate for a single-consumer service.

### What the processed topic carries and why it matters

After successfully embedding an event, the embedding service publishes to `contextengine.knowledge.processed`. This is not just a notification that "event X was processed." It carries the complete original event fields plus the list of Qdrant point IDs created.

The knowledge-graph-service (built in Sprint 4) reads from this topic. It needs:
- The original event fields (source type, author, timestamp, URL) to create Neo4j nodes for the Person, Document, and Concept entities
- The Qdrant point IDs so the Document node in Neo4j can reference which vectors represent it in the vector store

Publishing the enriched event to a separate topic rather than polling a database is the event-driven pattern working as intended: the embedding service is the authority on what was embedded, and it broadcasts that fact. Any service that cares — now or in the future — subscribes to the processed topic. No polling, no tight coupling, no shared database queries between services.

### Performance fundamentals for vector search

There are three performance characteristics that matter in a vector search system, and understanding them helps you make informed trade-offs.

**Recall vs. latency.** HNSW is an approximate nearest neighbor algorithm — it finds very good matches, but not guaranteed-exact matches. The `ef` parameter controls the tradeoff: higher `ef` means the algorithm explores more of the graph and finds more accurate results, but takes longer. For a knowledge retrieval system, 95% recall at 20ms latency is better than 99% recall at 200ms latency — users will not notice if one occasionally relevant document is missed, but they will notice slow responses.

**Embedding latency.** On a local machine without a GPU, Ollama takes 50-200ms to produce one embedding using the CPU. This is acceptable for offline batch processing (the Kafka consumer) but would be unacceptable for real-time query handling. Sprint 4's query service will call Ollama to embed the user's question before searching Qdrant. On a machine with a modern GPU, this drops to under 5ms.

**Index build vs. query.** Qdrant builds the HNSW graph incrementally as points are upserted. Adding points is slower when the index is large because the graph must be reconnected. This is why search performance degrades gracefully (logarithmic) but indexing throughput decreases as the collection grows. For a knowledge base that is mostly read (queries) rather than written (indexing), this is a favorable tradeoff.

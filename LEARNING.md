
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

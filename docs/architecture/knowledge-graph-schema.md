
# Knowledge Graph Schema

ContextEngine uses Neo4j to model relationships between the people, documents, decisions, and concepts that make up an organization's institutional memory. This document defines every node type, every relationship type, and the properties each carries.

## Design Principles

The graph schema is intentionally sparse at the property level. Properties that change frequently — view counts, last-accessed timestamps — do not belong in the graph. The graph's value is in its structure: who knows what, who decided what, what concepts relate to what systems. Everything else lives in PostgreSQL or Qdrant.

All nodes carry an `organizationId` property. Every query against Neo4j filters by this property to enforce tenant isolation. This is not optional — it is enforced at the repository layer.

## Node Types

### Person

Represents a human contributor in the organization. Created when a connector encounters a new author ID from any source.

```cypher
(:Person {
  id: String,               // Internal UUID
  organizationId: String,   // Tenant isolation
  sourceAuthorId: String,   // The ID from the originating system (Slack user ID, GitHub login)
  name: String,             // Display name
  email: String,            // Optional, if available from source
  department: String,       // Optional, populated from user service
  createdAt: DateTime,
  updatedAt: DateTime
})
```

### Document

Represents a discrete knowledge artifact — a Slack message, a PR description, a Jira ticket, a Confluence page. One KnowledgeEvent maps to one Document node. The full content lives in Qdrant; the graph node stores only metadata.

```cypher
(:Document {
  id: String,               // Internal UUID
  organizationId: String,
  sourceId: String,         // Original system identifier
  sourceType: String,       // SLACK | GITHUB | JIRA | CONFLUENCE | WEBHOOK
  title: String,            // Derived: PR title, ticket summary, first line of message
  url: String,              // Deep link back to the source
  qdrantPointId: String,    // Reference to the embedding in Qdrant
  timestamp: DateTime,
  createdAt: DateTime
})
```

### Concept

Represents a technical concept, system name, or domain term extracted from document content via NER. Concepts form the semantic backbone of the graph — they are the shared vocabulary across all source documents.

```cypher
(:Concept {
  id: String,
  organizationId: String,
  name: String,             // Normalized: lowercase, singularized ("auth services" -> "auth service")
  type: String,             // SYSTEM | TECHNOLOGY | PROCESS | BUSINESS_TERM
  occurrenceCount: Int,     // How many documents reference this concept
  createdAt: DateTime,
  updatedAt: DateTime
})
```

### Decision

A special subtype of knowledge event that represents an explicit architectural or product decision. Decisions are created when the entity extraction pipeline detects decision language ("we decided", "the team agreed", "going forward we will") or when documents are tagged as decision records.

```cypher
(:Decision {
  id: String,
  organizationId: String,
  summary: String,          // One-sentence summary of the decision
  rationale: String,        // Extracted or provided rationale
  documentId: String,       // The source document that contains this decision
  decidedAt: DateTime,
  createdAt: DateTime
})
```

### Repository

Represents a GitHub or GitLab repository. Acts as a hub connecting all pull requests, commits, and engineers associated with a codebase.

```cypher
(:Repository {
  id: String,
  organizationId: String,
  name: String,
  fullName: String,         // e.g., "acme-corp/payment-service"
  url: String,
  language: String,         // Primary language
  createdAt: DateTime
})
```

### Channel

Represents a Slack channel or other communication channel. Acts as a hub connecting all messages and participants.

```cypher
(:Channel {
  id: String,
  organizationId: String,
  name: String,
  sourceChannelId: String,
  sourceType: String,       // SLACK | TEAMS | DISCORD
  purpose: String,          // Channel description/purpose
  createdAt: DateTime
})
```

## Relationship Types

### AUTHORED

Connects a Person to a Document they created.

```cypher
(:Person)-[:AUTHORED {timestamp: DateTime}]->(:Document)
```

### MENTIONS

Connects a Document to a Concept extracted from its content.

```cypher
(:Document)-[:MENTIONS {weight: Float, count: Int}]->(:Concept)
```

`weight` represents how prominently the concept appears (based on frequency and position). `count` is the raw occurrence count in the document.

### REFERENCES

Connects a Document to another Document it explicitly links to or cites.

```cypher
(:Document)-[:REFERENCES {context: String}]->(:Document)
```

`context` is the surrounding sentence that contained the reference, to preserve why one document references another.

### DECIDED

Connects a Document to a Decision it contains or represents.

```cypher
(:Document)-[:DECIDED]->(:Decision)
```

### PARTICIPATED_IN

Connects a Person to a Channel they have posted in.

```cypher
(:Person)-[:PARTICIPATED_IN {messageCount: Int, lastActive: DateTime}]->(:Channel)
```

### POSTED_IN

Connects a Document to the Channel where it was published.

```cypher
(:Document)-[:POSTED_IN]->(:Channel)
```

### CONTRIBUTED_TO

Connects a Person to a Repository they have contributed pull requests or commits to.

```cypher
(:Person)-[:CONTRIBUTED_TO {commitCount: Int, prCount: Int, lastCommit: DateTime}]->(:Repository)
```

### BELONGS_TO

Connects a Document (specifically a pull request or commit) to its Repository.

```cypher
(:Document)-[:BELONGS_TO]->(:Repository)
```

### RELATED_TO

A general-purpose relationship between two Concepts that frequently co-occur across documents.

```cypher
(:Concept)-[:RELATED_TO {coOccurrenceCount: Int, strength: Float}]->(:Concept)
```

## Indexes

The following indexes are created at startup by the knowledge-graph-service initialization routine:

```cypher
CREATE INDEX person_org_idx IF NOT EXISTS FOR (p:Person) ON (p.organizationId, p.sourceAuthorId);
CREATE INDEX document_org_idx IF NOT EXISTS FOR (d:Document) ON (d.organizationId, d.sourceId);
CREATE INDEX document_type_idx IF NOT EXISTS FOR (d:Document) ON (d.organizationId, d.sourceType);
CREATE INDEX concept_name_idx IF NOT EXISTS FOR (c:Concept) ON (c.organizationId, c.name);
CREATE INDEX decision_org_idx IF NOT EXISTS FOR (dec:Decision) ON (dec.organizationId);
CREATE INDEX repository_org_idx IF NOT EXISTS FOR (r:Repository) ON (r.organizationId, r.fullName);
```

## Example Traversal Queries

Find everything a departing engineer has touched in the last six months:

```cypher
MATCH (p:Person {organizationId: $orgId, sourceAuthorId: $authorId})-[:AUTHORED]->(d:Document)
WHERE d.timestamp > datetime() - duration({months: 6})
OPTIONAL MATCH (d)-[:DECIDED]->(dec:Decision)
OPTIONAL MATCH (d)-[:MENTIONS]->(c:Concept)
RETURN d, dec, collect(distinct c.name) as concepts
ORDER BY d.timestamp DESC
```

Find who knows the most about a given system concept:

```cypher
MATCH (c:Concept {organizationId: $orgId, name: $conceptName})<-[:MENTIONS]-(d:Document)<-[:AUTHORED]-(p:Person)
RETURN p.name, count(d) as documentCount
ORDER BY documentCount DESC
LIMIT 10
```

Find what decisions relate to a given concept:

```cypher
MATCH (c:Concept {organizationId: $orgId, name: $conceptName})<-[:MENTIONS]-(d:Document)-[:DECIDED]->(dec:Decision)
RETURN dec.summary, dec.rationale, d.url, d.timestamp
ORDER BY d.timestamp DESC
```

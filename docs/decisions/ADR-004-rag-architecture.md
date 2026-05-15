
# ADR-004: Retrieval-Augmented Generation for Query Answering

**Status:** Accepted
**Date:** 2024-05-14
**Authors:** Platform Engineering Team

## Context

ContextEngine must answer natural language questions about organizational knowledge. The answer must be grounded in actual company data — not generic knowledge — and must include citations so users can verify and read the source material. The system must not hallucinate information that does not exist in the knowledge base.

Three technical approaches were evaluated for building the query answering capability:

1. Fine-tuning a language model on company knowledge
2. Full-context LLM (sending all relevant documents in a very large context window)
3. Retrieval-Augmented Generation (RAG)

## Decision

We will use Retrieval-Augmented Generation (RAG) as the query answering architecture.

## Rationale

**Fine-tuning** involves taking a base language model and training it further on company-specific data so that the fine-tuned model "knows" the company's information. This approach has fundamental problems for a live knowledge management system. First, fine-tuning is expensive — each training run costs money, time, and significant compute. Second, and more critically, fine-tuned knowledge is baked into the model weights, which means every time new knowledge is added (which in ContextEngine happens continuously), the model must be retrained. A pull request merged today would not be queryable until the next fine-tuning run. Third, fine-tuned models still hallucinate — they blend their training data with their base knowledge in ways that are difficult to distinguish or cite. There is no clean way to say "this answer comes from that specific Slack message" when the answer comes from model weights.

**Full-context LLM** involves retrieving all potentially relevant documents and stuffing them into a large context window. With models supporting 128k+ token contexts, this is technically feasible for small knowledge bases. The problems are latency and cost. Sending 128k tokens to a local LLM for every query would take many seconds on consumer hardware. More importantly, research consistently shows that LLM performance degrades when relevant information is buried in a long context — models tend to "lose" facts in the middle of very long inputs. This approach also does not scale: a large organization might have millions of indexed documents, and no context window is large enough for all of them.

**RAG** retrieves only the most relevant knowledge chunks before answering. The query is embedded into a vector, and semantic search retrieves the top-k most similar chunks from the knowledge base (typically 5–10 chunks). These chunks — which represent at most a few thousand tokens — are included in the prompt to the LLM. The LLM is instructed to answer only from the provided context and to cite its sources. This approach has three critical properties:

First, knowledge is always fresh. New documents are embedded and indexed continuously. They become searchable within seconds of being processed, with no model retraining required.

Second, every answer is traceable. The chunks used to generate the answer are included in the API response as source citations. Users can click through to the original Slack message, PR, or Jira ticket.

Third, the system can be instructed to say "I don't know" when the retrieved context does not contain a relevant answer. This prevents hallucination — the LLM has no reason to invent information when the prompt makes clear that it should only use the provided context.

ContextEngine augments standard RAG with graph context. After retrieving vector-similar chunks from Qdrant, the query-service also queries Neo4j for related context: who authored the retrieved documents, what other concepts they reference, what decisions they connect to. This graph-augmented context improves answer quality for questions about organizational relationships and decision history.

## Consequences

- The quality of answers is bounded by the quality of retrieval. If the embedding model fails to retrieve the most relevant chunks, the LLM cannot synthesize a correct answer. Retrieval quality must be evaluated and monitored.
- Chunking strategy matters. Documents chunked naively at fixed character boundaries will split sentences and lose semantic coherence. The embedding-service uses overlapping chunks (512 tokens, 50-token overlap) to preserve context at chunk boundaries.
- The LLM used for answer generation (llama3.1:8b via Ollama) must be instructed carefully via system prompt to avoid using its parametric knowledge and to cite sources. This is prompt engineering, not model fine-tuning.
- RAG performance scales with the quality and coverage of the knowledge base. During the initial period before substantial knowledge has been ingested, answers will be sparse. This is expected behavior, not a bug.
- The system should expose a confidence score with each answer, derived from the cosine similarity scores of the top retrieved chunks, so users can calibrate how much to trust an answer.

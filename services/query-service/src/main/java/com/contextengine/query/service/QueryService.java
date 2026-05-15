
package com.contextengine.query.service;

import com.contextengine.query.api.dto.QueryRequest;
import com.contextengine.query.api.dto.QueryResponse;
import com.contextengine.query.api.dto.SourceDocument;
import com.contextengine.query.audit.AuditService;
import com.contextengine.query.service.GraphContextService.GraphContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full RAG pipeline:
 *   1. Check Redis cache
 *   2. Embed the question via Ollama
 *   3. Search Qdrant for the most relevant chunks
 *   4. Enrich with Neo4j graph context (concepts, people)
 *   5. Call the LLM with the grounded prompt
 *   6. Cache and return the result
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final EmbeddingClient embeddingClient;
    private final VectorSearchService vectorSearch;
    private final GraphContextService graphContext;
    private final LlmService llmService;
    private final QueryCacheService cacheService;
    private final AuditService auditService;

    public QueryResponse query(QueryRequest request, String organizationId) {
        log.info("Query received: org={}, question length={}", organizationId, request.question().length());

        Optional<QueryResponse> cached = cacheService.get(organizationId, request.question());
        if (cached.isPresent()) {
            log.info("Cache hit for org={}", organizationId);
            QueryResponse hit = cached.get();
            auditService.record("QUERY_CACHE_HIT", null, null,
                    safeUuid(organizationId), "KNOWLEDGE_BASE", organizationId,
                    Map.of("question_length", request.question().length(), "source_count", hit.sources().size()),
                    null);
            // Return a new record with cacheHit=true (the stored record always has cacheHit=false)
            return new QueryResponse(hit.answer(), hit.sources(), hit.confidence(),
                    hit.relatedConcepts(), hit.relatedPeople(), true);
        }

        List<Float> queryVector = embeddingClient.embed(request.question());

        List<SourceDocument> sources = vectorSearch.search(
                organizationId, queryVector, request.effectiveMaxResults());

        if (sources.isEmpty()) {
            log.info("No relevant documents found in Qdrant for org={}", organizationId);
            return new QueryResponse(
                    "I don't have enough information about this in our knowledge base.",
                    List.of(), 0.0, List.of(), List.of(), false);
        }

        List<String> sourceIds = sources.stream().map(SourceDocument::sourceId).distinct().toList();
        GraphContext context = graphContext.getContextForSources(organizationId, sourceIds);

        String answer = llmService.generateAnswer(
                request.question(), sources, context.concepts(), context.people());

        double confidence = sources.stream()
                .mapToDouble(SourceDocument::relevanceScore)
                .limit(3)
                .average()
                .orElse(0.0);

        QueryResponse response = new QueryResponse(
                answer, sources, confidence,
                context.concepts(), context.people(), false);

        cacheService.put(organizationId, request.question(), response);

        auditService.record("QUERY_EXECUTED", null, null,
                safeUuid(organizationId), "KNOWLEDGE_BASE", organizationId,
                Map.of("question_length", request.question().length(),
                        "source_count", sources.size(),
                        "confidence", confidence),
                null);

        log.info("Query complete: org={}, sources={}, confidence={}",
                organizationId, sources.size(), confidence);

        return response;
    }

    private static UUID safeUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

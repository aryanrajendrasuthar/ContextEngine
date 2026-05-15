
package com.contextengine.query.service;

import com.contextengine.query.api.dto.SourceDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Searches Qdrant for the most semantically similar chunks to a query vector.
 * Uses the Qdrant REST API (port 6333). One collection per organization.
 */
@Slf4j
@Service
public class VectorSearchService {

    private final WebClient qdrantClient;
    private final int topK;

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-zA-Z0-9]");

    public VectorSearchService(
            @Qualifier("qdrantWebClient") WebClient qdrantClient,
            @Value("${qdrant.top-k:8}") int topK) {
        this.qdrantClient = qdrantClient;
        this.topK = topK;
    }

    public List<SourceDocument> search(String organizationId, List<Float> queryVector, int limit) {
        String collection = collectionName(organizationId);
        int effectiveLimit = limit > 0 ? limit : topK;

        log.debug("Searching Qdrant: collection={}, limit={}", collection, effectiveLimit);

        Map<String, Object> body = Map.of(
                "vector", queryVector,
                "limit", effectiveLimit,
                "with_payload", true
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = qdrantClient.post()
                .uri("/collections/{name}/points/search", collection)
                .bodyValue(Objects.requireNonNull(body))
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .onErrorResume(ex -> {
                    log.error("Qdrant search failed for collection={}: {}", collection, ex.getMessage());
                    return reactor.core.publisher.Mono.empty();
                })
                .block();

        if (response == null) return List.of();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("result");
        if (results == null) return List.of();

        List<SourceDocument> sources = new ArrayList<>();
        for (Map<String, Object> point : results) {
            SourceDocument doc = mapToSourceDocument(point);
            if (doc != null) sources.add(doc);
        }

        log.info("Qdrant returned {} results for org={}", sources.size(), organizationId);
        return sources;
    }

    @SuppressWarnings("unchecked")
    private SourceDocument mapToSourceDocument(Map<String, Object> point) {
        try {
            Map<String, Object> payload = (Map<String, Object>) point.get("payload");
            if (payload == null) return null;

            double score = ((Number) Objects.requireNonNull(point.get("score"))).doubleValue();
            String sourceId = (String) payload.getOrDefault("source_id", "");
            String sourceType = (String) payload.getOrDefault("source_type", "UNKNOWN");
            String content = (String) payload.getOrDefault("content", "");
            String authorName = (String) payload.get("author_name");
            String url = (String) payload.get("url");

            String timestampStr = (String) payload.get("timestamp");
            Instant timestamp = null;
            if (timestampStr != null) {
                try { timestamp = Instant.parse(timestampStr); } catch (Exception ignored) {}
            }

            String excerpt = content.length() > 500 ? content.substring(0, 500) + "..." : content;

            return new SourceDocument(sourceId, sourceType, excerpt, authorName, url, timestamp, score);
        } catch (Exception e) {
            log.warn("Failed to map Qdrant result to SourceDocument: {}", e.getMessage());
            return null;
        }
    }

    static String collectionName(String organizationId) {
        return "org_" + NON_ALNUM.matcher(organizationId).replaceAll("_");
    }
}


package com.contextengine.query.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The answer to a natural language query with full source attribution")
public record QueryResponse(

        @Schema(description = "The generated answer grounded in the retrieved documents")
        String answer,

        @Schema(description = "Source documents that were used to generate the answer, ordered by relevance")
        List<SourceDocument> sources,

        @Schema(description = "Average relevance score of the retrieved sources (0.0–1.0). Higher means more confident.", example = "0.87")
        double confidence,

        @Schema(description = "Technical concepts related to this question, derived from the knowledge graph")
        List<String> relatedConcepts,

        @Schema(description = "People who have authored documents relevant to this question")
        List<String> relatedPeople,

        @Schema(description = "True if this response was served from the Redis cache")
        boolean cacheHit
) {}

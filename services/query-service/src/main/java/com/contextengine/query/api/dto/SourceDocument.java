
package com.contextengine.query.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A source document that contributed to the answer")
public record SourceDocument(

        @Schema(description = "Unique identifier in the source system", example = "github-pr-acme-payment-service-42")
        String sourceId,

        @Schema(description = "Type of source: GITHUB, SLACK, JIRA, CONFLUENCE, WEBHOOK")
        String sourceType,

        @Schema(description = "The relevant excerpt from this source that informed the answer")
        String excerpt,

        @Schema(description = "Author of the source document")
        String authorName,

        @Schema(description = "Deep link to the original document")
        String url,

        @Schema(description = "When the source document was created or last updated")
        Instant timestamp,

        @Schema(description = "Cosine similarity score between this chunk and the query (0.0–1.0)")
        double relevanceScore
) {}


package com.contextengine.ingestion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A discrete unit of organizational knowledge from any connected source system")
public record KnowledgeEvent(

        @NotBlank
        @Schema(description = "Unique identifier within the source system", example = "github-pr-acme-payment-service-1042")
        String sourceId,

        @NotNull
        @Schema(description = "The type of source system this event originated from")
        SourceType sourceType,

        @NotBlank
        @Size(max = 100_000)
        @Schema(description = "Full text content of the knowledge artifact")
        String content,

        @NotBlank
        @Schema(description = "Organization identifier for tenant isolation", example = "org-acme-corp")
        String organizationId,

        @Schema(description = "Author identifier within the source system", example = "U0XYZ789")
        String authorId,

        @Schema(description = "Human-readable author name", example = "Jane Smith")
        String authorName,

        @NotNull
        @Schema(description = "Timestamp when this event occurred in the source system")
        Instant timestamp,

        @Size(max = 2000)
        @Schema(description = "Deep link URL back to the original source artifact")
        String url,

        @Schema(description = "Source-specific metadata fields (channel name, PR number, ticket key, etc.)")
        Map<String, String> metadata
) {}


package com.contextengine.query.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A natural language question to be answered against the organization's knowledge base")
public record QueryRequest(

        @NotBlank
        @Size(max = 2000)
        @Schema(description = "The question in natural language", example = "Why did we switch from PostgreSQL sequences to UUIDs?")
        String question,

        @Schema(description = "Maximum number of source chunks to retrieve from the vector store (1–20)", example = "8")
        Integer maxResults
) {
    public int effectiveMaxResults() {
        if (maxResults == null || maxResults < 1) return 8;
        return Math.min(maxResults, 20);
    }
}


package com.contextengine.ingestion.api.dto;

import com.contextengine.ingestion.service.IngestionService.IngestionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response returned after an ingestion attempt")
public record IngestionResponse(

        @Schema(description = "Outcome of the ingestion attempt")
        IngestionResult result,

        @Schema(description = "Human-readable message describing the outcome")
        String message,

        @Schema(description = "The sourceId of the processed event")
        String sourceId
) {
    public static IngestionResponse accepted(String sourceId) {
        return new IngestionResponse(IngestionResult.ACCEPTED,
                "Event accepted and queued for processing", sourceId);
    }

    public static IngestionResponse duplicate(String sourceId) {
        return new IngestionResponse(IngestionResult.DUPLICATE,
                "Event already exists and was not reprocessed", sourceId);
    }
}

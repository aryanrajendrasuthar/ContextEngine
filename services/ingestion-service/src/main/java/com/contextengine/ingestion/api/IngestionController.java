
package com.contextengine.ingestion.api;

import com.contextengine.ingestion.api.dto.IngestionResponse;
import com.contextengine.ingestion.model.KnowledgeEvent;
import com.contextengine.ingestion.service.IngestionService;
import com.contextengine.ingestion.service.IngestionService.IngestionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Knowledge Ingestion", description = "Endpoints for submitting knowledge events for processing")
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping
    @Operation(
            summary = "Submit a knowledge event",
            description = "Accepts a single KnowledgeEvent, validates it, deduplicates, and publishes it to the processing pipeline. Returns 202 for new events and 200 for duplicates.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Event accepted and queued for embedding"),
            @ApiResponse(responseCode = "200", description = "Event already exists, not reprocessed"),
            @ApiResponse(responseCode = "400", description = "Validation error — missing required fields or invalid format"),
            @ApiResponse(responseCode = "500", description = "Internal error — event could not be persisted or published")
    })
    public ResponseEntity<IngestionResponse> ingestEvent(@Valid @RequestBody KnowledgeEvent event) {
        IngestionResult result = ingestionService.ingest(event);

        if (result == IngestionResult.DUPLICATE) {
            return ResponseEntity.ok(IngestionResponse.duplicate(event.sourceId()));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(IngestionResponse.accepted(event.sourceId()));
    }

    @PostMapping("/batch")
    @Operation(
            summary = "Submit a batch of knowledge events",
            description = "Accepts up to 100 knowledge events in a single request. Each event is processed independently — duplicates are silently skipped.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Batch accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Validation error in one or more events")
    })
    public ResponseEntity<BatchIngestionResponse> ingestBatch(
            @Valid @RequestBody List<@Valid KnowledgeEvent> events) {

        if (events.size() > 100) {
            return ResponseEntity.badRequest()
                    .body(new BatchIngestionResponse(0, 0, events.size(), "Batch size exceeds maximum of 100"));
        }

        int accepted = 0;
        int duplicates = 0;

        for (KnowledgeEvent event : events) {
            IngestionResult result = ingestionService.ingest(event);
            if (result == IngestionResult.ACCEPTED) {
                accepted++;
            } else {
                duplicates++;
            }
        }

        log.info("Batch ingestion complete: accepted={}, duplicates={}, total={}", accepted, duplicates, events.size());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BatchIngestionResponse(accepted, duplicates, events.size(), null));
    }

    public record BatchIngestionResponse(int accepted, int duplicates, int total, String error) {}
}

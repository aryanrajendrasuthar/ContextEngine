
package com.contextengine.query.api;

import com.contextengine.query.api.dto.QueryRequest;
import com.contextengine.query.api.dto.QueryResponse;
import com.contextengine.query.service.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@Tag(name = "Query", description = "Natural language query over the organization's knowledge base")
public class QueryController {

    private final QueryService queryService;

    @PostMapping
    @Operation(
            summary = "Ask a question",
            description = "Embeds the question, retrieves the most relevant knowledge chunks from Qdrant, " +
                          "enriches with Neo4j graph context, and generates a grounded answer via Ollama. " +
                          "Responses are cached for 1 hour per organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request — missing or blank question"),
            @ApiResponse(responseCode = "503", description = "Upstream dependency unavailable")
    })
    public ResponseEntity<QueryResponse> query(
            @Valid @RequestBody QueryRequest request,
            @RequestHeader("X-Organization-Id") String organizationId) {

        QueryResponse response = queryService.query(request, organizationId);
        return ResponseEntity.ok(response);
    }
}


package com.contextengine.connector.api;

import com.contextengine.connector.api.dto.ConnectorConfigResponse;
import com.contextengine.connector.api.dto.CreateConnectorRequest;
import com.contextengine.connector.service.ConnectorConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connectors")
@RequiredArgsConstructor
@Tag(name = "Connectors", description = "Manage data source connector configurations")
public class ConnectorConfigController {

    private final ConnectorConfigService service;

    @GetMapping
    @Operation(summary = "List all connectors for an organization")
    public ResponseEntity<List<ConnectorConfigResponse>> list(
            @Parameter(description = "Organization identifier", required = true)
            @RequestHeader("X-Organization-Id") String organizationId) {

        return ResponseEntity.ok(service.listByOrganization(organizationId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single connector by ID")
    public ResponseEntity<ConnectorConfigResponse> getById(
            @PathVariable UUID id,
            @RequestHeader("X-Organization-Id") String organizationId) {

        return ResponseEntity.ok(service.getById(id, organizationId));
    }

    @PostMapping
    @Operation(summary = "Create a new connector")
    public ResponseEntity<ConnectorConfigResponse> create(
            @Valid @RequestBody CreateConnectorRequest request,
            @RequestHeader("X-Organization-Id") String organizationId,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {

        ConnectorConfigResponse response = service.create(request, organizationId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update connector name and configuration")
    public ResponseEntity<ConnectorConfigResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateConnectorRequest request,
            @RequestHeader("X-Organization-Id") String organizationId) {

        return ResponseEntity.ok(service.update(id, organizationId, request));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a connector so the scheduler picks it up")
    public ResponseEntity<ConnectorConfigResponse> activate(
            @PathVariable UUID id,
            @RequestHeader("X-Organization-Id") String organizationId) {

        return ResponseEntity.ok(service.activate(id, organizationId));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a connector without deleting it")
    public ResponseEntity<ConnectorConfigResponse> deactivate(
            @PathVariable UUID id,
            @RequestHeader("X-Organization-Id") String organizationId) {

        return ResponseEntity.ok(service.deactivate(id, organizationId));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test connectivity to the source system")
    public ResponseEntity<Map<String, Object>> test(
            @PathVariable UUID id,
            @RequestHeader("X-Organization-Id") String organizationId) {

        boolean healthy = service.testConnection(id, organizationId);
        return ResponseEntity.ok(Map.of("connectorId", id, "healthy", healthy));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a connector configuration permanently")
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("X-Organization-Id") String organizationId) {

        service.delete(id, organizationId);
    }
}

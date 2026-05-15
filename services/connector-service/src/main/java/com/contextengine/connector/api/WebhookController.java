
package com.contextengine.connector.api;

import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorStatus;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.model.KnowledgeEvent;
import com.contextengine.connector.repository.ConnectorConfigRepository;
import com.contextengine.connector.service.IngestionClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Receive push events from external source systems")
public class WebhookController {

    private final ConnectorConfigRepository configRepository;
    private final IngestionClient ingestionClient;

    /**
     * Generic webhook endpoint. External systems POST their events to
     * /api/v1/webhooks/{connectorId}. The connector must exist, be of type WEBHOOK,
     * and be ACTIVE. The raw payload is forwarded directly to the ingestion service.
     */
    @PostMapping("/{connectorId}")
    @Operation(summary = "Receive a webhook event from an external source system")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
            @PathVariable UUID connectorId,
            @RequestHeader(value = "X-Organization-Id", required = false) String orgIdHeader,
            @RequestBody Map<String, Object> payload) {

        ConnectorConfig config = configRepository.findById(java.util.Objects.requireNonNull(connectorId))
                .orElse(null);

        if (config == null) {
            log.warn("Webhook received for unknown connector: {}", connectorId);
            return ResponseEntity.notFound().build();
        }

        if (config.getConnectorType() != ConnectorType.WEBHOOK) {
            log.warn("Webhook received for non-webhook connector: {}", connectorId);
            return ResponseEntity.badRequest().body(Map.of("error", "Connector is not a webhook type"));
        }

        if (config.getStatus() != ConnectorStatus.ACTIVE) {
            log.warn("Webhook received for inactive connector: {}", connectorId);
            return ResponseEntity.badRequest().body(Map.of("error", "Connector is not active"));
        }

        String eventId = payload.getOrDefault("id", UUID.randomUUID().toString()).toString();
        String content = buildContent(payload);

        KnowledgeEvent event = new KnowledgeEvent(
                "webhook-" + connectorId + "-" + eventId,
                ConnectorType.WEBHOOK.name(),
                content,
                config.getOrganizationId(),
                null,
                null,
                Instant.now(),
                null,
                Map.of("connectorId", connectorId.toString(), "rawEventId", eventId)
        );

        ingestionClient.sendSingle(event);

        log.info("Webhook event received and forwarded: connector={}, eventId={}", connectorId, eventId);
        return ResponseEntity.accepted().body(Map.of("status", "accepted", "eventId", eventId));
    }

    private String buildContent(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        payload.forEach((k, v) -> {
            if (v != null) {
                sb.append(k).append(": ").append(v).append("\n");
            }
        });
        return sb.toString().trim();
    }

    /**
     * Returns the list of connectors configured as WEBHOOK type for an organization,
     * so the caller knows which endpoint URL to configure in their source system.
     */
    @GetMapping
    @Operation(summary = "List webhook endpoints available for the organization")
    public ResponseEntity<List<Map<String, Object>>> listWebhooks(
            @RequestHeader("X-Organization-Id") String organizationId) {

        List<Map<String, Object>> endpoints = configRepository
                .findByOrganizationId(organizationId)
                .stream()
                .filter(c -> c.getConnectorType() == ConnectorType.WEBHOOK)
                .map(c -> Map.<String, Object>of(
                        "connectorId", c.getId(),
                        "name", c.getName(),
                        "status", c.getStatus(),
                        "endpointPath", "/api/v1/webhooks/" + c.getId()
                ))
                .toList();

        return ResponseEntity.ok(endpoints);
    }
}

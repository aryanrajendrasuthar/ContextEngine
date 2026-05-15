
package com.contextengine.connector.api.dto;

import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorStatus;
import com.contextengine.connector.model.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Connector configuration including runtime status")
public record ConnectorConfigResponse(

        @Schema(description = "Unique connector identifier")
        UUID id,

        @Schema(description = "Organization this connector belongs to")
        String organizationId,

        @Schema(description = "Human-readable connector name")
        String name,

        @Schema(description = "Connector type")
        ConnectorType connectorType,

        @Schema(description = "Source-specific configuration. Sensitive fields such as tokens are redacted.")
        Map<String, String> config,

        @Schema(description = "Current operational status")
        ConnectorStatus status,

        @Schema(description = "Timestamp of the most recent completed sync")
        Instant lastSyncAt,

        @Schema(description = "Result of the last sync run: SUCCESS or FAILED")
        String lastSyncStatus,

        @Schema(description = "Error detail from the last failed sync, if any")
        String errorMessage,

        @Schema(description = "Cumulative number of events successfully forwarded to the ingestion service")
        Long documentsIndexed,

        @Schema(description = "User ID that created this connector")
        String createdBy,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last modification timestamp")
        Instant updatedAt
) {

    private static final java.util.Set<String> SENSITIVE_KEYS =
            java.util.Set.of("token", "apiToken", "secret", "password", "apiKey");

    public static ConnectorConfigResponse from(ConnectorConfig entity) {
        Map<String, String> redactedConfig = new java.util.LinkedHashMap<>(entity.getConfig());
        redactedConfig.replaceAll((k, v) ->
                SENSITIVE_KEYS.stream().anyMatch(k::equalsIgnoreCase) ? "***" : v);

        return new ConnectorConfigResponse(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getName(),
                entity.getConnectorType(),
                redactedConfig,
                entity.getStatus(),
                entity.getLastSyncAt(),
                entity.getLastSyncStatus(),
                entity.getErrorMessage(),
                entity.getDocumentsIndexed(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}


package com.contextengine.connector.api.dto;

import com.contextengine.connector.model.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Schema(description = "Request body for creating or updating a connector configuration")
public record CreateConnectorRequest(

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Human-readable name for this connector, unique within the organization", example = "Main GitHub Repo")
        String name,

        @NotNull
        @Schema(description = "The type of data source this connector integrates with")
        ConnectorType connectorType,

        @NotNull
        @Schema(description = "Source-specific configuration key-value pairs. For GitHub: 'repo', 'token'. For Slack: 'channel', 'token'. For Jira: 'apiUrl', 'username', 'token'.")
        Map<String, String> config
) {}

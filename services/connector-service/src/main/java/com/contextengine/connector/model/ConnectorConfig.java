
package com.contextengine.connector.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "connectors",
        indexes = {
                @Index(name = "idx_conn_org_id", columnList = "organization_id"),
                @Index(name = "idx_conn_status", columnList = "status"),
                @Index(name = "idx_conn_type", columnList = "connector_type")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorConfig {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 255)
    private String organizationId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 50)
    private ConnectorType connectorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> config;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ConnectorStatus status = ConnectorStatus.INACTIVE;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_status", length = 50)
    private String lastSyncStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "documents_indexed", nullable = false)
    @Builder.Default
    private Long documentsIndexed = 0L;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}


package com.contextengine.ingestion.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "knowledge_events",
        indexes = {
                @Index(name = "idx_ke_org_source_id", columnList = "organization_id, source_id", unique = true),
                @Index(name = "idx_ke_content_hash", columnList = "content_hash"),
                @Index(name = "idx_ke_status", columnList = "status"),
                @Index(name = "idx_ke_timestamp", columnList = "timestamp DESC"),
                @Index(name = "idx_ke_org_source_type", columnList = "organization_id, source_type")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeEventEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, length = 255)
    private String organizationId;

    @Column(name = "source_id", nullable = false, length = 500)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "author_id", length = 255)
    private String authorId;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "url", length = 2000)
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IngestionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}


package com.contextengine.user.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String eventType;

    private UUID actorId;
    private String actorEmail;
    private UUID organizationId;

    @Column(length = 100)
    private String resourceType;

    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();
}

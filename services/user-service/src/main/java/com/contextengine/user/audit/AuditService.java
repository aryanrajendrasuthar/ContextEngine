
package com.contextengine.user.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String eventType,
            UUID actorId,
            String actorEmail,
            UUID organizationId,
            String resourceType,
            String resourceId,
            Map<String, Object> details,
            String ipAddress) {

        try {
            AuditLog entry = new AuditLog();
            entry.setEventType(eventType);
            entry.setActorId(actorId);
            entry.setActorEmail(actorEmail);
            entry.setOrganizationId(organizationId);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            entry.setDetails(details);
            entry.setIpAddress(ipAddress);
            auditLogRepository.save(entry);

            log.debug("Audit: type={}, actor={}, org={}", eventType, actorEmail, organizationId);
        } catch (Exception e) {
            log.error("Failed to record audit event type={}: {}", eventType, e.getMessage());
        }
    }
}

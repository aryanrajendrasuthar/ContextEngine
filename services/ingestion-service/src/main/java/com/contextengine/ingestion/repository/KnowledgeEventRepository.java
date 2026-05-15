
package com.contextengine.ingestion.repository;

import com.contextengine.ingestion.model.IngestionStatus;
import com.contextengine.ingestion.model.KnowledgeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeEventRepository extends JpaRepository<KnowledgeEventEntity, UUID> {

    boolean existsByOrganizationIdAndSourceId(String organizationId, String sourceId);

    Optional<KnowledgeEventEntity> findByOrganizationIdAndSourceId(String organizationId, String sourceId);

    boolean existsByContentHash(String contentHash);

    @Modifying
    @Query("UPDATE KnowledgeEventEntity e SET e.status = :status, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") IngestionStatus status);

    @Query("SELECT COUNT(e) FROM KnowledgeEventEntity e WHERE e.organizationId = :orgId")
    long countByOrganizationId(@Param("orgId") String organizationId);
}

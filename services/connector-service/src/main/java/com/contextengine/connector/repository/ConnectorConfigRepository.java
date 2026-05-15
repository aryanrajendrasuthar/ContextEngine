
package com.contextengine.connector.repository;

import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorConfigRepository extends JpaRepository<ConnectorConfig, UUID> {

    List<ConnectorConfig> findByOrganizationId(String organizationId);

    List<ConnectorConfig> findByStatus(ConnectorStatus status);

    Optional<ConnectorConfig> findByIdAndOrganizationId(UUID id, String organizationId);

    boolean existsByOrganizationIdAndName(String organizationId, String name);
}

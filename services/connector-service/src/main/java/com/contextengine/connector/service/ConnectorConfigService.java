
package com.contextengine.connector.service;

import com.contextengine.connector.api.dto.ConnectorConfigResponse;
import com.contextengine.connector.api.dto.CreateConnectorRequest;
import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorStatus;
import com.contextengine.connector.repository.ConnectorConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorConfigService {

    private final ConnectorConfigRepository repository;
    private final ConnectorRegistry registry;

    @Transactional(readOnly = true)
    public List<ConnectorConfigResponse> listByOrganization(String organizationId) {
        return repository.findByOrganizationId(organizationId)
                .stream()
                .map(ConnectorConfigResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConnectorConfigResponse getById(UUID id, String organizationId) {
        ConnectorConfig config = findOrThrow(id, organizationId);
        return ConnectorConfigResponse.from(config);
    }

    @Transactional
    public ConnectorConfigResponse create(CreateConnectorRequest request, String organizationId, String createdBy) {
        if (!registry.isSupported(request.connectorType())) {
            throw new IllegalArgumentException("Unsupported connector type: " + request.connectorType());
        }
        if (repository.existsByOrganizationIdAndName(organizationId, request.name())) {
            throw new IllegalArgumentException(
                    "A connector named '" + request.name() + "' already exists for this organization");
        }

        ConnectorConfig config = ConnectorConfig.builder()
                .organizationId(organizationId)
                .name(request.name())
                .connectorType(request.connectorType())
                .config(request.config())
                .status(ConnectorStatus.INACTIVE)
                .createdBy(createdBy)
                .build();

        ConnectorConfig saved = repository.save(config);
        log.info("Created connector: id={}, org={}, type={}", saved.getId(), organizationId, saved.getConnectorType());
        return ConnectorConfigResponse.from(saved);
    }

    @Transactional
    public ConnectorConfigResponse update(UUID id, String organizationId, CreateConnectorRequest request) {
        ConnectorConfig config = findOrThrow(id, organizationId);

        if (!config.getName().equals(request.name()) &&
                repository.existsByOrganizationIdAndName(organizationId, request.name())) {
            throw new IllegalArgumentException(
                    "A connector named '" + request.name() + "' already exists for this organization");
        }

        config.setName(request.name());
        config.setConfig(request.config());
        ConnectorConfig saved = repository.save(config);
        log.info("Updated connector: id={}, org={}", id, organizationId);
        return ConnectorConfigResponse.from(saved);
    }

    @Transactional
    public ConnectorConfigResponse activate(UUID id, String organizationId) {
        ConnectorConfig config = findOrThrow(id, organizationId);
        config.setStatus(ConnectorStatus.ACTIVE);
        config.setErrorMessage(null);
        ConnectorConfig saved = repository.save(config);
        log.info("Activated connector: id={}", id);
        return ConnectorConfigResponse.from(saved);
    }

    @Transactional
    public ConnectorConfigResponse deactivate(UUID id, String organizationId) {
        ConnectorConfig config = findOrThrow(id, organizationId);
        config.setStatus(ConnectorStatus.INACTIVE);
        ConnectorConfig saved = repository.save(config);
        log.info("Deactivated connector: id={}", id);
        return ConnectorConfigResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String organizationId) {
        ConnectorConfig config = findOrThrow(id, organizationId);
        repository.delete(config);
        log.info("Deleted connector: id={}, org={}", id, organizationId);
    }

    public boolean testConnection(UUID id, String organizationId) {
        ConnectorConfig config = findOrThrow(id, organizationId);
        ConnectorInterface connector = registry.getConnector(config.getConnectorType())
                .orElseThrow(() -> new IllegalStateException(
                        "No implementation for connector type: " + config.getConnectorType()));
        boolean healthy = connector.testConnection(config);
        log.info("Connection test for connector={}: {}", id, healthy ? "PASS" : "FAIL");
        return healthy;
    }

    private ConnectorConfig findOrThrow(UUID id, String organizationId) {
        return repository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Connector not found: id=" + id + ", org=" + organizationId));
    }
}

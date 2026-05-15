
package com.contextengine.connector.service;

import com.contextengine.connector.api.dto.ConnectorConfigResponse;
import com.contextengine.connector.api.dto.CreateConnectorRequest;
import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorStatus;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.repository.ConnectorConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorConfigServiceTest {

    @Mock
    private ConnectorConfigRepository repository;

    @Mock
    private ConnectorRegistry registry;

    private ConnectorConfigService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorConfigService(repository, registry);
    }

    private ConnectorConfig sampleConfig(UUID id) {
        ConnectorConfig config = ConnectorConfig.builder()
                .organizationId("org-acme")
                .name("GitHub Main")
                .connectorType(ConnectorType.GITHUB)
                .config(Map.of("repo", "acme/platform"))
                .status(ConnectorStatus.INACTIVE)
                .documentsIndexed(0L)
                .build();
        config.setId(id);
        config.setCreatedAt(java.time.Instant.now());
        config.setUpdatedAt(java.time.Instant.now());
        return config;
    }

    @Test
    void create_savesConnector_andReturnsResponse() {
        when(registry.isSupported(ConnectorType.GITHUB)).thenReturn(true);
        when(repository.existsByOrganizationIdAndName("org-acme", "GitHub Main")).thenReturn(false);
        when(repository.save(any(ConnectorConfig.class))).thenAnswer(inv -> {
            ConnectorConfig c = java.util.Objects.requireNonNull(inv.<ConnectorConfig>getArgument(0));
            c.setId(UUID.randomUUID());
            c.setCreatedAt(java.time.Instant.now());
            c.setUpdatedAt(java.time.Instant.now());
            return c;
        });

        CreateConnectorRequest request = new CreateConnectorRequest(
                "GitHub Main", ConnectorType.GITHUB, Map.of("repo", "acme/platform"));

        ConnectorConfigResponse response = service.create(request, "org-acme", "user-1");

        assertThat(response.name()).isEqualTo("GitHub Main");
        assertThat(response.connectorType()).isEqualTo(ConnectorType.GITHUB);
        assertThat(response.status()).isEqualTo(ConnectorStatus.INACTIVE);
        assertThat(response.organizationId()).isEqualTo("org-acme");
        verify(repository).save(java.util.Objects.requireNonNull(any(ConnectorConfig.class)));
    }

    @Test
    void create_unsupportedType_throwsIllegalArgument() {
        when(registry.isSupported(ConnectorType.JIRA)).thenReturn(false);

        CreateConnectorRequest request = new CreateConnectorRequest(
                "Jira Connector", ConnectorType.JIRA, Map.of());

        assertThatThrownBy(() -> service.create(request, "org-acme", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported connector type");
    }

    @Test
    void create_duplicateName_throwsIllegalArgument() {
        when(registry.isSupported(ConnectorType.GITHUB)).thenReturn(true);
        when(repository.existsByOrganizationIdAndName("org-acme", "GitHub Main")).thenReturn(true);

        CreateConnectorRequest request = new CreateConnectorRequest(
                "GitHub Main", ConnectorType.GITHUB, Map.of());

        assertThatThrownBy(() -> service.create(request, "org-acme", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void activate_changesStatusToActive() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = sampleConfig(id);
        when(repository.findByIdAndOrganizationId(id, "org-acme")).thenReturn(Optional.of(config));
        when(repository.save(any(ConnectorConfig.class))).thenAnswer(
                inv -> java.util.Objects.requireNonNull(inv.<ConnectorConfig>getArgument(0)));

        ConnectorConfigResponse response = service.activate(id, "org-acme");

        assertThat(response.status()).isEqualTo(ConnectorStatus.ACTIVE);
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void deactivate_changesStatusToInactive() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = sampleConfig(id);
        config.setStatus(ConnectorStatus.ACTIVE);
        when(repository.findByIdAndOrganizationId(id, "org-acme")).thenReturn(Optional.of(config));
        when(repository.save(any(ConnectorConfig.class))).thenAnswer(
                inv -> java.util.Objects.requireNonNull(inv.<ConnectorConfig>getArgument(0)));

        ConnectorConfigResponse response = service.deactivate(id, "org-acme");

        assertThat(response.status()).isEqualTo(ConnectorStatus.INACTIVE);
    }

    @Test
    void getById_notFound_throwsNoSuchElement() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(id, "org-acme")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id, "org-acme"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testConnection_delegatesToConnector() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = sampleConfig(id);
        ConnectorInterface mockConnector = mock(ConnectorInterface.class);

        when(repository.findByIdAndOrganizationId(id, "org-acme")).thenReturn(Optional.of(config));
        when(registry.getConnector(ConnectorType.GITHUB)).thenReturn(Optional.of(mockConnector));
        when(mockConnector.testConnection(config)).thenReturn(true);

        boolean healthy = service.testConnection(id, "org-acme");

        assertThat(healthy).isTrue();
        verify(mockConnector).testConnection(config);
    }

    @Test
    void listByOrganization_returnsAllForOrg() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.findByOrganizationId("org-acme"))
                .thenReturn(List.of(sampleConfig(id1), sampleConfig(id2)));

        List<ConnectorConfigResponse> results = service.listByOrganization("org-acme");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.organizationId().equals("org-acme"));
    }

    @Test
    void response_redactsSensitiveConfigFields() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = sampleConfig(id);
        config.setConfig(Map.of("repo", "acme/platform", "token", "ghp_secret123"));
        when(repository.findByIdAndOrganizationId(id, "org-acme")).thenReturn(Optional.of(config));

        ConnectorConfigResponse response = service.getById(id, "org-acme");

        assertThat(response.config()).containsEntry("repo", "acme/platform");
        assertThat(response.config()).containsEntry("token", "***");
    }

    @Test
    void delete_removesConnector() {
        UUID id = UUID.randomUUID();
        ConnectorConfig config = sampleConfig(id);
        when(repository.findByIdAndOrganizationId(id, "org-acme")).thenReturn(Optional.of(config));

        service.delete(id, "org-acme");

        verify(repository).delete(java.util.Objects.requireNonNull(config));
    }
}

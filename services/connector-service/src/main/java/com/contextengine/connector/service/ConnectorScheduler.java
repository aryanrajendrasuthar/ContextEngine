
package com.contextengine.connector.service;

import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.exception.ConnectorException;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorStatus;
import com.contextengine.connector.model.KnowledgeEvent;
import com.contextengine.connector.repository.ConnectorConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorScheduler {

    private final ConnectorConfigRepository configRepository;
    private final ConnectorRegistry registry;
    private final IngestionClient ingestionClient;

    @Scheduled(fixedDelayString = "${connectors.scheduler.interval-ms:60000}")
    @Transactional
    public void runAllActiveConnectors() {
        List<ConnectorConfig> activeConnectors = configRepository.findByStatus(ConnectorStatus.ACTIVE);

        if (activeConnectors.isEmpty()) {
            log.debug("No active connectors to run");
            return;
        }

        log.info("Running scheduled sync for {} active connectors", activeConnectors.size());

        for (ConnectorConfig config : activeConnectors) {
            try {
                syncConnector(config);
            } catch (Exception e) {
                log.error("Uncaught error syncing connector={}: {}", config.getId(), e.getMessage(), e);
                markConnectorError(config, e.getMessage());
            }
        }
    }

    private void syncConnector(ConnectorConfig config) {
        ConnectorInterface connector = registry.getConnector(config.getConnectorType())
                .orElseThrow(() -> new IllegalStateException(
                        "No connector implementation for type: " + config.getConnectorType()));

        Instant since = config.getLastSyncAt() != null
                ? config.getLastSyncAt()
                : Instant.now().minus(30, ChronoUnit.DAYS);

        log.info("Syncing connector={}, type={}, since={}", config.getId(), config.getConnectorType(), since);

        config.setStatus(ConnectorStatus.SYNCING);
        configRepository.save(config);

        List<KnowledgeEvent> events;
        try {
            events = connector.fetchEvents(config, since);
        } catch (ConnectorException e) {
            log.error("Connector fetch failed: connector={}, error={}", config.getId(), e.getMessage());
            markConnectorError(config, e.getMessage());
            return;
        }

        if (!events.isEmpty()) {
            ingestionClient.sendBatch(events);
            config.setDocumentsIndexed(config.getDocumentsIndexed() + events.size());
        }

        config.setStatus(ConnectorStatus.ACTIVE);
        config.setLastSyncAt(Instant.now());
        config.setLastSyncStatus("SUCCESS");
        config.setErrorMessage(null);
        configRepository.save(config);

        log.info("Connector sync complete: connector={}, eventsFound={}", config.getId(), events.size());
    }

    private void markConnectorError(ConnectorConfig config, String errorMessage) {
        config.setStatus(ConnectorStatus.ERROR);
        config.setLastSyncAt(Instant.now());
        config.setLastSyncStatus("FAILED");
        config.setErrorMessage(errorMessage);
        configRepository.save(config);
    }
}

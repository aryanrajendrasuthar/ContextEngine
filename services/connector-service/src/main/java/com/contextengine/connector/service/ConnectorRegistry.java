
package com.contextengine.connector.service;

import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.model.ConnectorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds all registered connector implementations.
 * Spring injects all ConnectorInterface beans automatically — adding a new connector
 * type requires only creating a new @Component that implements ConnectorInterface.
 * No configuration changes needed here.
 */
@Slf4j
@Service
public class ConnectorRegistry {

    private final Map<ConnectorType, ConnectorInterface> connectors;

    public ConnectorRegistry(List<ConnectorInterface> connectorList) {
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(ConnectorInterface::getType, Function.identity()));
        log.info("Registered connectors: {}", connectors.keySet());
    }

    public Optional<ConnectorInterface> getConnector(ConnectorType type) {
        return Optional.ofNullable(connectors.get(type));
    }

    public boolean isSupported(ConnectorType type) {
        return connectors.containsKey(type);
    }
}

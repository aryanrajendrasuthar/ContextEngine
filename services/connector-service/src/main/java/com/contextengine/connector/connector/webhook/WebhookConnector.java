
package com.contextengine.connector.connector.webhook;

import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.exception.ConnectorException;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.model.KnowledgeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Webhook connector. Unlike polling connectors, webhooks receive events pushed by
 * the source system. This connector's fetchEvents returns an empty list because
 * webhook events are received directly through the WebhookController endpoint.
 * The connector exists in the registry so its configuration can be managed
 * and its connection status tracked.
 */
@Slf4j
@Component
public class WebhookConnector implements ConnectorInterface {

    @Override
    public ConnectorType getType() {
        return ConnectorType.WEBHOOK;
    }

    @Override
    public List<KnowledgeEvent> fetchEvents(ConnectorConfig config, Instant since) throws ConnectorException {
        // Webhook connectors do not poll — events are pushed to /api/v1/webhooks/{connectorId}
        return List.of();
    }

    @Override
    public boolean testConnection(ConnectorConfig config) {
        return true;
    }
}

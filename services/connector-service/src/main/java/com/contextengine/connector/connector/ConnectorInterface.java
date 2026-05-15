
package com.contextengine.connector.connector;

import com.contextengine.connector.exception.ConnectorException;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.model.KnowledgeEvent;

import java.time.Instant;
import java.util.List;

/**
 * All source connectors implement this interface.
 * A connector's responsibility is narrow: given a configuration and a "fetch since" timestamp,
 * return all new knowledge events from the source system. The connector does not validate, store,
 * or publish events — that is the ingestion service's job.
 */
public interface ConnectorInterface {

    ConnectorType getType();

    /**
     * Fetch knowledge events created or updated since the given timestamp.
     * Must be idempotent — calling this twice with the same parameters must produce the same result.
     * Must handle rate limits, pagination, and transient errors internally.
     */
    List<KnowledgeEvent> fetchEvents(ConnectorConfig config, Instant since) throws ConnectorException;

    /**
     * Verify that the connector can reach the source system with the provided configuration.
     * Returns true if the connection is healthy, false otherwise. Must not throw.
     */
    boolean testConnection(ConnectorConfig config);
}

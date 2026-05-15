
package com.contextengine.ingestion.model;

public enum IngestionStatus {
    RECEIVED,
    EMBEDDING,
    EMBEDDED,
    GRAPH_PROCESSED,
    PROCESSED,   // terminal success state set by the ingestion-service status consumer
    DUPLICATE,
    FAILED
}

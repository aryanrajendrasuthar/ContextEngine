
package com.contextengine.ingestion.model;

public enum IngestionStatus {
    RECEIVED,
    EMBEDDING,
    EMBEDDED,
    GRAPH_PROCESSED,
    DUPLICATE,
    FAILED
}

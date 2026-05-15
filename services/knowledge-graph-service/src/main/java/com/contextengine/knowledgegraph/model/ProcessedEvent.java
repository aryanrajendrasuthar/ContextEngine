
package com.contextengine.knowledgegraph.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the ProcessedEvent published to contextengine.knowledge.processed by the Python
 * embedding-service. Field names use @JsonProperty to handle the mixed camelCase/snake_case
 * that Pydantic's model_dump() produces.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessedEvent(

        @JsonProperty("sourceId")
        String sourceId,

        @JsonProperty("sourceType")
        String sourceType,

        @JsonProperty("content")
        String content,

        @JsonProperty("organizationId")
        String organizationId,

        @JsonProperty("authorId")
        String authorId,

        @JsonProperty("authorName")
        String authorName,

        @JsonProperty("timestamp")
        Instant timestamp,

        @JsonProperty("url")
        String url,

        @JsonProperty("metadata")
        Map<String, Object> metadata,

        @JsonProperty("qdrant_point_ids")
        List<String> qdrantPointIds,

        @JsonProperty("chunk_count")
        int chunkCount,

        @JsonProperty("processing_timestamp")
        Instant processingTimestamp
) {}


package com.contextengine.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeEvent(
        String sourceId,
        String sourceType,
        String content,
        String organizationId,
        String authorId,
        String authorName,
        Instant timestamp,
        String url,
        Map<String, String> metadata
) {}

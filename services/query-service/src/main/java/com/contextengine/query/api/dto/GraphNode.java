
package com.contextengine.query.api.dto;

import java.util.Map;

public record GraphNode(
        String id,
        String label,
        String type,
        Map<String, Object> properties
) {}

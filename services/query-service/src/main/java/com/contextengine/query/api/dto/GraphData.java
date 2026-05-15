
package com.contextengine.query.api.dto;

import java.util.List;

public record GraphData(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {}

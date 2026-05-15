
package com.contextengine.knowledgegraph.service;

import com.contextengine.knowledgegraph.graph.GraphRepository;
import com.contextengine.knowledgegraph.model.ProcessedEvent;
import com.contextengine.knowledgegraph.service.EntityExtractor.ExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphConsumerService {

    private final GraphRepository graphRepository;
    private final EntityExtractor entityExtractor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.knowledge-processed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        ProcessedEvent event = null;
        try {
            event = objectMapper.readValue(message, ProcessedEvent.class);
            processEvent(event);
        } catch (Exception e) {
            String sourceId = event != null ? event.sourceId() : "unknown";
            log.error("Failed to process knowledge graph event: sourceId={}, error={}",
                    sourceId, e.getMessage(), e);
        } finally {
            ack.acknowledge();
        }
    }

    private void processEvent(ProcessedEvent event) {
        log.info("Building graph nodes for: sourceId={}, org={}, sourceType={}",
                event.sourceId(), event.organizationId(), event.sourceType());

        String title = deriveTitle(event);
        String primaryPointId = event.qdrantPointIds() != null && !event.qdrantPointIds().isEmpty()
                ? event.qdrantPointIds().get(0) : null;

        graphRepository.mergeDocument(
                event.organizationId(), event.sourceId(), event.sourceType(),
                title, event.url(), primaryPointId, event.timestamp());

        if (event.authorId() != null || event.authorName() != null) {
            String authorId = event.authorId() != null ? event.authorId() : event.authorName();
            graphRepository.mergePerson(event.organizationId(), authorId, event.authorName());
            graphRepository.mergeAuthoredRelationship(event.organizationId(), authorId, event.sourceId());
        }

        ExtractionResult extraction = entityExtractor.extract(event.content());

        for (String concept : extraction.concepts()) {
            graphRepository.mergeConcept(event.organizationId(), concept);
            graphRepository.mergeMentionsRelationship(event.organizationId(), event.sourceId(), concept);
        }

        if (extraction.containsDecision()) {
            String summary = extractDecisionSummary(event.content());
            graphRepository.mergeDecision(event.organizationId(), event.sourceId(), summary);
            log.debug("Created Decision node for sourceId={}", event.sourceId());
        }

        log.info("Graph update complete: sourceId={}, concepts={}, decision={}",
                event.sourceId(), extraction.concepts().size(), extraction.containsDecision());
    }

    private String deriveTitle(ProcessedEvent event) {
        String content = event.content();
        if (content == null || content.isBlank()) return event.sourceId();
        String firstLine = content.lines().findFirst().orElse(event.sourceId()).strip();
        return firstLine.length() > 120 ? firstLine.substring(0, 120) + "..." : firstLine;
    }

    private String extractDecisionSummary(String content) {
        for (String sentence : content.split("[.!?]")) {
            String trimmed = sentence.strip();
            if (trimmed.length() > 10) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return content.length() > 200 ? content.substring(0, 200) : content;
    }
}

package com.contextengine.ingestion.service;

import com.contextengine.ingestion.model.IngestionStatus;
import com.contextengine.ingestion.repository.KnowledgeEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens on the processed topic published by the embedding-service and marks
 * the corresponding KnowledgeEventEntity as PROCESSED so the status endpoint
 * reflects pipeline completion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingStatusConsumer {

    private final KnowledgeEventRepository repository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.knowledge-processed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "statusKafkaListenerContainerFactory"
    )
    @Transactional
    public void onProcessed(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String sourceId = node.path("sourceId").asText(null);
            String organizationId = node.path("organizationId").asText(null);

            if (sourceId != null && organizationId != null) {
                repository.findByOrganizationIdAndSourceId(organizationId, sourceId)
                        .ifPresent(entity -> {
                            repository.updateStatus(entity.getId(), IngestionStatus.PROCESSED);
                            log.debug("Marked event PROCESSED: sourceId={}, org={}", sourceId, organizationId);
                        });
            }
        } catch (Exception e) {
            log.warn("Failed to update processing status from Kafka message: {}", e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }
}

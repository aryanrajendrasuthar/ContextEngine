
package com.contextengine.ingestion.service;

import com.contextengine.ingestion.model.IngestionStatus;
import com.contextengine.ingestion.model.KnowledgeEvent;
import com.contextengine.ingestion.model.KnowledgeEventEntity;
import com.contextengine.ingestion.repository.KnowledgeEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class IngestionService {

    private final KnowledgeEventRepository repository;
    private final DeduplicationService deduplicationService;
    private final KafkaProducerService kafkaProducerService;
    private final Counter receivedCounter;
    private final Counter duplicateCounter;

    public IngestionService(
            KnowledgeEventRepository repository,
            DeduplicationService deduplicationService,
            KafkaProducerService kafkaProducerService,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.deduplicationService = deduplicationService;
        this.kafkaProducerService = kafkaProducerService;
        this.receivedCounter = Counter.builder("knowledge_events_received_total")
                .description("Total knowledge events received by the ingestion service")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("knowledge_events_duplicate_total")
                .description("Total knowledge events rejected as duplicates")
                .register(meterRegistry);
    }

    public enum IngestionResult {
        ACCEPTED, DUPLICATE
    }

    @Transactional
    public IngestionResult ingest(KnowledgeEvent event) {
        receivedCounter.increment();

        String contentHash = deduplicationService.computeContentHash(event.content());

        if (deduplicationService.isDuplicate(event.organizationId(), event.sourceId(), contentHash)) {
            duplicateCounter.increment();
            log.debug("Rejected duplicate event: organizationId={}, sourceId={}",
                    event.organizationId(), event.sourceId());
            return IngestionResult.DUPLICATE;
        }

        KnowledgeEventEntity entity = KnowledgeEventEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(event.organizationId())
                .sourceId(event.sourceId())
                .sourceType(event.sourceType())
                .content(event.content())
                .contentHash(contentHash)
                .authorId(event.authorId())
                .authorName(event.authorName())
                .timestamp(event.timestamp())
                .url(event.url())
                .metadata(event.metadata())
                .status(IngestionStatus.RECEIVED)
                .build();

        repository.save(entity);
        deduplicationService.markSeen(contentHash);

        kafkaProducerService.publishToRawTopic(event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed for sourceId={}, marking FAILED", event.sourceId(), ex);
                        repository.updateStatus(entity.getId(), IngestionStatus.FAILED);
                    }
                });

        log.info("Accepted knowledge event: organizationId={}, sourceId={}, sourceType={}",
                event.organizationId(), event.sourceId(), event.sourceType());

        return IngestionResult.ACCEPTED;
    }

    public Optional<KnowledgeEventEntity> findBySourceId(String organizationId, String sourceId) {
        return repository.findByOrganizationIdAndSourceId(organizationId, sourceId);
    }
}


package com.contextengine.ingestion.service;

import com.contextengine.ingestion.model.KnowledgeEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, KnowledgeEvent> kafkaTemplate;
    private final String rawTopic;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public KafkaProducerService(
            KafkaTemplate<String, KnowledgeEvent> kafkaTemplate,
            @Value("${kafka.topics.knowledge-raw}") String rawTopic,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.rawTopic = rawTopic;
        this.publishedCounter = Counter.builder("knowledge_events_published_total")
                .description("Total knowledge events published to Kafka")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("knowledge_events_publish_failed_total")
                .description("Total knowledge events that failed to publish to Kafka")
                .register(meterRegistry);
    }

    public CompletableFuture<SendResult<String, KnowledgeEvent>> publishToRawTopic(KnowledgeEvent event) {
        // Partition key is organizationId to preserve ordering within an organization
        return kafkaTemplate.send(rawTopic, event.organizationId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        publishedCounter.increment();
                        log.debug("Published to Kafka: topic={}, partition={}, offset={}, sourceId={}",
                                rawTopic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                event.sourceId());
                    } else {
                        failedCounter.increment();
                        log.error("Failed to publish to Kafka: sourceId={}, error={}",
                                event.sourceId(), ex.getMessage(), ex);
                    }
                });
    }
}

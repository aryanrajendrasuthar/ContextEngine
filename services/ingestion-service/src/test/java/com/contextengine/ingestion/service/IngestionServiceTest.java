
package com.contextengine.ingestion.service;

import com.contextengine.ingestion.model.IngestionStatus;
import com.contextengine.ingestion.model.KnowledgeEvent;
import com.contextengine.ingestion.model.SourceType;
import com.contextengine.ingestion.repository.KnowledgeEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.contextengine.ingestion.model.KnowledgeEventEntity;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private KnowledgeEventRepository repository;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private KafkaProducerService kafkaProducerService;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(
                repository, deduplicationService, kafkaProducerService, new SimpleMeterRegistry());
    }

    private KnowledgeEvent sampleEvent() {
        return new KnowledgeEvent(
                "github-pr-1",
                SourceType.GITHUB,
                "Implements payment idempotency using Redis-backed keys",
                "org-acme",
                "U001",
                "Sarah Chen",
                Instant.now(),
                "https://github.com/acme/payment-service/pull/1",
                Map.of("pr", "1")
        );
    }

    @Test
    void newEvent_isAccepted_andPersisted() {
        KnowledgeEvent event = sampleEvent();
        String hash = "abc123hash";

        when(deduplicationService.computeContentHash(event.content())).thenReturn(hash);
        when(deduplicationService.isDuplicate(event.organizationId(), event.sourceId(), hash)).thenReturn(false);
        when(kafkaProducerService.publishToRawTopic(event))
                .thenReturn(CompletableFuture.completedFuture(null));

        IngestionService.IngestionResult result = ingestionService.ingest(event);

        assertThat(result).isEqualTo(IngestionService.IngestionResult.ACCEPTED);

        ArgumentCaptor<KnowledgeEventEntity> entityCaptor = ArgumentCaptor.forClass(KnowledgeEventEntity.class);
        verify(repository).save(java.util.Objects.requireNonNull(entityCaptor.capture()));
        KnowledgeEventEntity saved = entityCaptor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo("org-acme");
        assertThat(saved.getSourceId()).isEqualTo("github-pr-1");
        assertThat(saved.getContentHash()).isEqualTo(hash);
        assertThat(saved.getStatus()).isEqualTo(IngestionStatus.RECEIVED);

        verify(deduplicationService).markSeen(hash);
        verify(kafkaProducerService).publishToRawTopic(event);
    }

    @Test
    void duplicateEvent_isRejected_withoutPersisting() {
        KnowledgeEvent event = sampleEvent();
        String hash = "abc123hash";

        when(deduplicationService.computeContentHash(event.content())).thenReturn(hash);
        when(deduplicationService.isDuplicate(event.organizationId(), event.sourceId(), hash)).thenReturn(true);

        IngestionService.IngestionResult result = ingestionService.ingest(event);

        assertThat(result).isEqualTo(IngestionService.IngestionResult.DUPLICATE);
        verifyNoInteractions(kafkaProducerService);
        verify(repository, never()).save(any());
    }

    @Test
    void kafkaFailure_marksEntityAsFailed() {
        KnowledgeEvent event = sampleEvent();
        String hash = "abc123hash";

        when(deduplicationService.computeContentHash(event.content())).thenReturn(hash);
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaProducerService.publishToRawTopic(event))
                .thenAnswer(inv -> failedFuture);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ingestionService.ingest(event);

        // Give the whenComplete callback time to run (it's sync on CompletableFuture.completeExceptionally)
        verify(repository).updateStatus(idCaptor.capture(), eq(IngestionStatus.FAILED));
        assertThat(idCaptor.getValue()).isNotNull();
    }

    @Test
    void ingest_computesHash_fromEventContent() {
        KnowledgeEvent event = sampleEvent();

        when(deduplicationService.computeContentHash(any())).thenReturn("somehash");
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);
        when(kafkaProducerService.publishToRawTopic(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        ingestionService.ingest(event);

        verify(deduplicationService).computeContentHash(event.content());
        verify(deduplicationService).isDuplicate(event.organizationId(), event.sourceId(), "somehash");
    }
}

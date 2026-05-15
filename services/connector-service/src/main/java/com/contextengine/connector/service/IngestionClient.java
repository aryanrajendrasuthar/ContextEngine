
package com.contextengine.connector.service;

import com.contextengine.connector.model.KnowledgeEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Slf4j
@Service
public class IngestionClient {

    private final WebClient webClient;

    public IngestionClient(
            WebClient.Builder webClientBuilder,
            @Value("${ingestion.service.url}") String ingestionServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(ingestionServiceUrl).build();
    }

    @CircuitBreaker(name = "ingestion-service", fallbackMethod = "sendBatchFallback")
    public void sendBatch(List<KnowledgeEvent> events) {
        if (events.isEmpty()) return;

        try {
            webClient.post()
                    .uri("/api/v1/events/batch")
                    .bodyValue(events)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Sent {} events to ingestion-service", events.size());
        } catch (WebClientResponseException e) {
            log.error("Ingestion-service returned error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    public void sendSingle(KnowledgeEvent event) {
        sendBatch(List.of(event));
    }

    private void sendBatchFallback(List<KnowledgeEvent> events, Throwable t) {
        log.error("Circuit breaker open for ingestion-service. Dropping {} events. Cause: {}",
                events.size(), t.getMessage());
    }
}


package com.contextengine.query.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Calls Ollama to convert a text string into a 768-dimensional embedding vector.
 * Used to embed the user's question before searching Qdrant.
 */
@Slf4j
@Service
public class EmbeddingClient {

    private final WebClient webClient;
    private final String model;

    public EmbeddingClient(
            WebClient.Builder webClientBuilder,
            @Value("${ollama.base-url}") String ollamaBaseUrl,
            @Value("${ollama.embedding-model}") String model) {
        this.webClient = webClientBuilder.baseUrl(java.util.Objects.requireNonNull(ollamaBaseUrl)).build();
        this.model = model;
    }

    @SuppressWarnings("unchecked")
    public List<Float> embed(String text) {
        log.debug("Embedding query text ({} chars) with model={}", text.length(), model);

        Map<String, Object> response = webClient.post()
                .uri("/api/embeddings")
                .bodyValue(java.util.Objects.requireNonNull(Map.of("model", model, "prompt", text)))
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .block();

        if (response == null || !response.containsKey("embedding")) {
            throw new IllegalStateException("Ollama returned no embedding for model: " + model);
        }

        List<Number> raw = (List<Number>) response.get("embedding");
        return raw.stream().map(Number::floatValue).toList();
    }
}

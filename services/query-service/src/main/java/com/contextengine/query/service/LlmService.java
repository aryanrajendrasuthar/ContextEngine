
package com.contextengine.query.service;

import com.contextengine.query.api.dto.SourceDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calls Ollama's chat API to generate a grounded answer from retrieved context.
 * The prompt is designed to prevent hallucination: the model is instructed to
 * answer only from the provided context and to cite sources by number.
 */
@Slf4j
@Service
public class LlmService {

    private final WebClient webClient;
    private final String model;

    public LlmService(
            WebClient.Builder builder,
            @Value("${ollama.base-url}") String ollamaBaseUrl,
            @Value("${ollama.chat-model}") String model) {
        this.webClient = builder.baseUrl(Objects.requireNonNull(ollamaBaseUrl)).build();
        this.model = model;
    }

    public String generateAnswer(String question, List<SourceDocument> sources,
                                  List<String> relatedConcepts, List<String> relatedPeople) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(question, sources, relatedConcepts, relatedPeople);

        log.debug("Calling Ollama model={} with {} source chunks", model, sources.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
                .uri("/api/chat")
                .bodyValue(Objects.requireNonNull(Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)
                        ),
                        "stream", false
                )))
                .retrieve()
                .bodyToMono(Map.class)
                .cast(Map.class)
                .map(m -> (Map<String, Object>) m)
                .block();

        if (response == null) {
            return "I was unable to generate an answer at this time. The language model did not respond.";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) response.get("message");
        if (message == null) {
            return "I was unable to generate an answer at this time.";
        }

        return (String) message.getOrDefault("content",
                "I was unable to generate an answer at this time.");
    }

    private String buildSystemPrompt() {
        return """
                You are a precise knowledge assistant for an engineering organization.
                Answer the user's question using ONLY the context documents provided below.
                For each claim you make, cite the source number in brackets, e.g. [1] or [2].
                If the provided context does not contain enough information to answer the question,
                say exactly: "I don't have enough information about this in our knowledge base."
                Do not speculate, infer, or use knowledge outside the provided context.
                Be concise and direct. Prefer bullet points for multi-part answers.
                """;
    }

    private String buildUserPrompt(String question, List<SourceDocument> sources,
                                    List<String> concepts, List<String> people) {
        StringBuilder sb = new StringBuilder();

        sb.append("Context documents:\n\n");
        for (int i = 0; i < sources.size(); i++) {
            SourceDocument s = sources.get(i);
            sb.append(String.format("[%d] (%s", i + 1, s.sourceType()));
            if (s.authorName() != null) sb.append(" — ").append(s.authorName());
            if (s.timestamp() != null) sb.append(", ").append(s.timestamp().toString().substring(0, 10));
            sb.append("):\n").append(s.excerpt()).append("\n\n");
        }

        if (!people.isEmpty()) {
            sb.append("People with relevant knowledge on this topic: ")
              .append(String.join(", ", people)).append("\n\n");
        }

        if (!concepts.isEmpty()) {
            sb.append("Related technical concepts: ")
              .append(String.join(", ", concepts)).append("\n\n");
        }

        sb.append("Question: ").append(question).append("\n\nAnswer:");
        return sb.toString();
    }
}

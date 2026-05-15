
package com.contextengine.ingestion.api;

import com.contextengine.ingestion.model.KnowledgeEvent;
import com.contextengine.ingestion.model.SourceType;
import com.contextengine.ingestion.service.IngestionService;
import com.contextengine.ingestion.service.IngestionService.IngestionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerIntegrationTest {

    @org.springframework.lang.NonNull
    private static final MediaType JSON =
            java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngestionService ingestionService;

    @org.springframework.lang.NonNull
    private String json(Object value) throws Exception {
        return java.util.Objects.requireNonNull(objectMapper.writeValueAsString(value));
    }

    private KnowledgeEvent sampleEvent() {
        return new KnowledgeEvent(
                "github-pr-42",
                SourceType.GITHUB,
                "Implement retry logic with exponential backoff for payment processor calls",
                "org-acme",
                "U001",
                "Sarah Chen",
                Instant.parse("2024-11-01T10:00:00Z"),
                "https://github.com/acme/payment-service/pull/42",
                Map.of("pr", "42", "repo", "payment-service")
        );
    }

    @Test
    void postEvent_accepted_returns202() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(IngestionResult.ACCEPTED);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(JSON)
                        .content(json(sampleEvent())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result").value("ACCEPTED"))
                .andExpect(jsonPath("$.sourceId").value("github-pr-42"));
    }

    @Test
    void postEvent_duplicate_returns200() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(IngestionResult.DUPLICATE);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(JSON)
                        .content(json(sampleEvent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
    }

    @Test
    void postEvent_missingSourceId_returns400() throws Exception {
        String invalidJson = """
                {
                  "sourceId": "",
                  "sourceType": "GITHUB",
                  "content": "some content",
                  "organizationId": "org-acme",
                  "timestamp": "2024-11-01T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postEvent_missingOrganizationId_returns400() throws Exception {
        String invalidJson = """
                {
                  "sourceId": "github-pr-42",
                  "sourceType": "GITHUB",
                  "content": "some content",
                  "organizationId": "",
                  "timestamp": "2024-11-01T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postBatch_returns202_withCounts() throws Exception {
        when(ingestionService.ingest(any()))
                .thenReturn(IngestionResult.ACCEPTED)
                .thenReturn(IngestionResult.DUPLICATE);

        List<KnowledgeEvent> batch = List.of(sampleEvent(),
                new KnowledgeEvent("github-pr-43", SourceType.GITHUB, "Second PR content",
                        "org-acme", null, null, Instant.now(), null, Map.of()));

        mockMvc.perform(post("/api/v1/events/batch")
                        .contentType(JSON)
                        .content(json(batch)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }
}

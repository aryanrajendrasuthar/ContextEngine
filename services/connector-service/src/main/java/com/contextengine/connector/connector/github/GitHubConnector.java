
package com.contextengine.connector.connector.github;

import com.contextengine.connector.connector.ConnectorInterface;
import com.contextengine.connector.exception.ConnectorException;
import com.contextengine.connector.model.ConnectorConfig;
import com.contextengine.connector.model.ConnectorType;
import com.contextengine.connector.model.KnowledgeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubConnector implements ConnectorInterface {

    private final WebClient.Builder webClientBuilder;

    @Override
    public ConnectorType getType() {
        return ConnectorType.GITHUB;
    }

    @Override
    public List<KnowledgeEvent> fetchEvents(ConnectorConfig config, Instant since) throws ConnectorException {
        String repo = config.getConfig().getOrDefault("repository", "");
        String token = config.getConfig().getOrDefault("token", "");
        String apiUrl = config.getConfig().getOrDefault("apiUrl", "https://api.github.com");

        if (repo.isBlank()) {
            log.warn("GitHub connector {} has no repository configured — returning mock events", config.getId());
            return GitHubMockData.generateMockPullRequests(config);
        }

        try {
            return fetchPullRequests(apiUrl, repo, token, since, config.getOrganizationId());
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(config.getId().toString(),
                    "Unexpected error fetching GitHub events from " + repo, e);
        }
    }

    @Override
    public boolean testConnection(ConnectorConfig config) {
        String apiUrl = config.getConfig().getOrDefault("apiUrl", "https://api.github.com");
        String token = config.getConfig().getOrDefault("token", "");
        try {
            WebClient client = buildClient(apiUrl, token);
            client.get()
                    .uri("/rate_limit")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("GitHub connection test failed for connector {}: {}", config.getId(), e.getMessage());
            return false;
        }
    }

    private List<KnowledgeEvent> fetchPullRequests(
            String apiUrl, String repo, String token, Instant since, String organizationId)
            throws ConnectorException {

        WebClient client = buildClient(apiUrl, token);
        List<KnowledgeEvent> events = new ArrayList<>();

        try {
            String sinceParam = DateTimeFormatter.ISO_INSTANT.format(since);
            List<Map<String, Object>> prs = client.get()
                    .uri("/repos/{repo}/pulls?state=closed&sort=updated&direction=desc&per_page=50", repo)
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .cast(Map.class)
                    .map(pr -> (Map<String, Object>) pr)
                    .filter(pr -> {
                        String updatedAt = (String) pr.get("updated_at");
                        return updatedAt != null && Instant.parse(updatedAt).isAfter(since);
                    })
                    .collectList()
                    .block();

            if (prs == null) return events;

            for (Map<String, Object> pr : prs) {
                KnowledgeEvent event = mapPrToEvent(pr, repo, organizationId);
                if (event != null) {
                    events.add(event);
                }
            }

            log.info("Fetched {} pull requests from GitHub repo={}", events.size(), repo);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 429) {
                throw new ConnectorException(repo,
                        "GitHub API rate limit exceeded. Configure a token to increase the limit.", e);
            }
            throw new ConnectorException(repo, "GitHub API error: " + e.getStatusCode(), e);
        }

        return events;
    }

    @SuppressWarnings("unchecked")
    private KnowledgeEvent mapPrToEvent(Map<String, Object> pr, String repo, String organizationId) {
        try {
            Number prNumber = (Number) pr.get("number");
            String title = (String) pr.get("title");
            String body = (String) pr.getOrDefault("body", "");
            String url = (String) pr.get("html_url");
            String updatedAt = (String) pr.get("updated_at");

            Map<String, Object> user = (Map<String, Object>) pr.get("user");
            String authorLogin = user != null ? (String) user.get("login") : "unknown";
            String authorId = user != null ? String.valueOf(user.get("id")) : "unknown";

            String content = title + "\n\n" + (body != null ? body : "");

            return new KnowledgeEvent(
                    "github-pr-" + repo.replace("/", "-") + "-" + prNumber,
                    ConnectorType.GITHUB.name(),
                    content,
                    organizationId,
                    authorId,
                    authorLogin,
                    Instant.parse(updatedAt),
                    url,
                    Map.of("repository", repo, "prNumber", String.valueOf(prNumber))
            );
        } catch (Exception e) {
            log.warn("Failed to map GitHub PR to KnowledgeEvent: {}", e.getMessage());
            return null;
        }
    }

    private WebClient buildClient(String apiUrl, String token) {
        return webClientBuilder.baseUrl(apiUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("Authorization", token.isBlank() ? "" : "Bearer " + token)
                .build();
    }
}

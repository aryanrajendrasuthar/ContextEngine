
package com.contextengine.query.service;

import com.contextengine.query.api.dto.QueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Caches query responses in Redis keyed by SHA-256(organizationId + question).
 * Cache TTL is configurable (default 1 hour). The cache is scoped per organization
 * so a question asked by org-acme cannot return cached results for org-beta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCacheService {

    private static final String KEY_PREFIX = "query:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${query.cache.ttl-seconds:3600}")
    private long cacheTtlSeconds;

    public Optional<QueryResponse> get(String organizationId, String question) {
        String key = cacheKey(organizationId, question);
        String cached = redisTemplate.opsForValue().get(java.util.Objects.requireNonNull(key));
        if (cached == null) return Optional.empty();

        try {
            QueryResponse response = objectMapper.readValue(cached, QueryResponse.class);
            log.debug("Query cache hit: org={}", organizationId);
            return Optional.of(response);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached query response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String organizationId, String question, QueryResponse response) {
        try {
            String key = cacheKey(organizationId, question);
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                    java.util.Objects.requireNonNull(key),
                    java.util.Objects.requireNonNull(value),
                    java.util.Objects.requireNonNull(Duration.ofSeconds(cacheTtlSeconds)));
            log.debug("Cached query response: org={}, ttl={}s", organizationId, cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("Failed to cache query response: {}", e.getMessage());
        }
    }

    private String cacheKey(String organizationId, String question) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = organizationId + ":" + question.strip().toLowerCase();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

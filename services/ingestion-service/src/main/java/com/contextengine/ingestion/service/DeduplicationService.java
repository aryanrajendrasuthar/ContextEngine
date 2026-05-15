
package com.contextengine.ingestion.service;

import com.contextengine.ingestion.repository.KnowledgeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private static final String DEDUP_KEY_PREFIX = "dedup:content:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final KnowledgeEventRepository repository;
    private final StringRedisTemplate redisTemplate;

    public String computeContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Returns true if this content hash has been seen recently.
     * Checks Redis first (fast, approximate) then falls back to PostgreSQL (authoritative).
     */
    public boolean isDuplicate(String organizationId, String sourceId, String contentHash) {
        // Fast path: check Redis bloom-filter-style cache
        String redisKey = DEDUP_KEY_PREFIX + contentHash;
        Boolean inCache = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(inCache)) {
            log.debug("Duplicate detected via Redis cache: contentHash={}", contentHash);
            return true;
        }

        // Authoritative path: check if this exact source ID was already ingested
        if (repository.existsByOrganizationIdAndSourceId(organizationId, sourceId)) {
            log.debug("Duplicate detected via database: organizationId={}, sourceId={}", organizationId, sourceId);
            markSeen(contentHash);
            return true;
        }

        return false;
    }

    public void markSeen(String contentHash) {
        String redisKey = DEDUP_KEY_PREFIX + contentHash;
        redisTemplate.opsForValue().set(redisKey, "1", DEDUP_TTL);
    }
}


package com.contextengine.user.api.dto;

import com.contextengine.user.model.ApiKey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String plainKey,   // only present on creation; null on subsequent reads
        Instant lastUsedAt,
        Instant expiresAt,
        Instant createdAt
) {
    public static ApiKeyResponse from(ApiKey key, String plainKey) {
        return new ApiKeyResponse(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                plainKey,
                key.getLastUsedAt(),
                key.getExpiresAt(),
                key.getCreatedAt()
        );
    }

    public static ApiKeyResponse from(ApiKey key) {
        return from(key, null);
    }
}

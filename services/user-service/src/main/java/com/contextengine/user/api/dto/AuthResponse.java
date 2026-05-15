
package com.contextengine.user.api.dto;

import com.contextengine.user.model.UserRole;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        String displayName,
        UUID organizationId,
        String organizationName,
        UserRole role
) {
    public static final String BEARER = "Bearer";
}

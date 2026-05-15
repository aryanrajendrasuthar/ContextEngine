
package com.contextengine.user.api.dto;

import com.contextengine.user.model.User;
import com.contextengine.user.model.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        boolean isActive,
        UUID organizationId,
        String organizationName,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isEnabled(),
                user.getOrganization().getId(),
                user.getOrganization().getName(),
                user.getCreatedAt()
        );
    }
}

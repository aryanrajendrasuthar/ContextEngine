
package com.contextengine.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyRequest(
        @NotBlank @Size(max = 255) String name
) {}

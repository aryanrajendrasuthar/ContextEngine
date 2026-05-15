
package com.contextengine.user.api;

import com.contextengine.user.api.dto.ApiKeyRequest;
import com.contextengine.user.api.dto.ApiKeyResponse;
import com.contextengine.user.api.dto.UserResponse;
import com.contextengine.user.model.User;
import com.contextengine.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current user profile and API key management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user's profile")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }

    @GetMapping("/me/api-keys")
    @Operation(summary = "List all API keys belonging to the authenticated user")
    public List<ApiKeyResponse> listApiKeys(@AuthenticationPrincipal User user) {
        return userService.listApiKeys(user);
    }

    @PostMapping("/me/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new API key — the plain key is only shown once")
    public ApiKeyResponse createApiKey(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ApiKeyRequest request) {
        return userService.createApiKey(user, request);
    }

    @DeleteMapping("/me/api-keys/{keyId}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Void> deleteApiKey(
            @AuthenticationPrincipal User user,
            @PathVariable UUID keyId) {
        userService.deleteApiKey(user, keyId);
        return ResponseEntity.noContent().build();
    }
}

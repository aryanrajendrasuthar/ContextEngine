
package com.contextengine.user.service;

import com.contextengine.user.api.dto.ApiKeyRequest;
import com.contextengine.user.api.dto.ApiKeyResponse;
import com.contextengine.user.model.ApiKey;
import com.contextengine.user.model.User;
import com.contextengine.user.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<ApiKeyResponse> listApiKeys(User user) {
        return apiKeyRepository.findByUser(user).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public ApiKeyResponse createApiKey(User user, ApiKeyRequest request) {
        if (apiKeyRepository.existsByUserAndName(user, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An API key with that name already exists");
        }

        byte[] rawBytes = new byte[32];
        secureRandom.nextBytes(rawBytes);
        String plainKey = "ce_" + Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

        String keyHash = hashKey(plainKey);
        String keyPrefix = plainKey.substring(0, Math.min(10, plainKey.length()));

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setOrganization(user.getOrganization());
        apiKey.setName(request.name());
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(keyPrefix);
        apiKeyRepository.save(apiKey);

        log.info("Created API key '{}' for user {}", request.name(), user.getEmail());
        return ApiKeyResponse.from(apiKey, plainKey);
    }

    @Transactional
    public void deleteApiKey(User user, UUID keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));

        if (!Objects.requireNonNull(key.getUser().getId()).equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        apiKeyRepository.delete(key);
        log.info("Deleted API key {} for user {}", keyId, user.getEmail());
    }

    private static String hashKey(String plainKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

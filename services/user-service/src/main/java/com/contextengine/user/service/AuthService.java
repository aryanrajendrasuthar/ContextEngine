
package com.contextengine.user.service;

import com.contextengine.user.api.dto.AuthResponse;
import com.contextengine.user.api.dto.LoginRequest;
import com.contextengine.user.api.dto.RegisterRequest;
import com.contextengine.user.audit.AuditService;
import com.contextengine.user.model.Organization;
import com.contextengine.user.model.User;
import com.contextengine.user.model.UserRole;
import com.contextengine.user.repository.OrganizationRepository;
import com.contextengine.user.repository.UserRepository;
import com.contextengine.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:user:";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        Organization org = new Organization();
        org.setName(request.organizationName());
        org.setSlug(generateUniqueSlug(request.organizationName()));
        organizationRepository.save(org);

        User user = new User();
        user.setOrganization(org);
        user.setEmail(request.email().toLowerCase(Locale.ROOT));
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);

        log.info("Registered user {} in organization {}", user.getEmail(), org.getSlug());
        auditService.record("USER_REGISTERED", user.getId(), user.getEmail(),
                org.getId(), "USER", user.getId().toString(),
                Map.of("organizationSlug", org.getSlug(), "role", user.getRole().name()), null);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User {} logged in", user.getEmail());
        auditService.record("USER_LOGIN", user.getId(), user.getEmail(),
                user.getOrganization().getId(), "USER", user.getId().toString(),
                Map.of("role", user.getRole().name()), null);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String userId = jwtService.extractSubject(refreshToken);
        String storedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);

        if (!refreshToken.equals(storedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }

        User user = userRepository.findById(Objects.requireNonNull(UUID.fromString(userId)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return issueTokens(user);
    }

    public void logout(String userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
        log.info("User {} logged out", userId);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        redisTemplate.opsForValue().set(
                Objects.requireNonNull(REFRESH_KEY_PREFIX + user.getId().toString()),
                Objects.requireNonNull(refreshToken),
                Objects.requireNonNull(Duration.ofSeconds(jwtService.getRefreshTokenExpirySeconds()))
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                AuthResponse.BEARER,
                jwtService.getRefreshTokenExpirySeconds(),
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getOrganization().getId(),
                user.getOrganization().getName(),
                user.getRole()
        );
    }

    private String generateUniqueSlug(String name) {
        String base = NON_ALPHANUMERIC.matcher(
                Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        ).replaceAll("-").replaceAll("^-|-$", "");

        String slug = base;
        int suffix = 2;
        while (organizationRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}

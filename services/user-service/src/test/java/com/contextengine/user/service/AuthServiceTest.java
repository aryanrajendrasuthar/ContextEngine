
package com.contextengine.user.service;

import com.contextengine.user.api.dto.AuthResponse;
import com.contextengine.user.api.dto.LoginRequest;
import com.contextengine.user.api.dto.RegisterRequest;
import com.contextengine.user.model.Organization;
import com.contextengine.user.model.User;
import com.contextengine.user.model.UserRole;
import com.contextengine.user.repository.OrganizationRepository;
import com.contextengine.user.repository.UserRepository;
import com.contextengine.user.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AuthService authService;

    private Organization org;
    private User user;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Acme Corp");
        org.setSlug("acme-corp");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setOrganization(org);
        user.setEmail("alice@acme.com");
        user.setPasswordHash("$2a$bcrypt$hash");
        user.setRole(UserRole.ADMIN);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void register_createsOrgAndUser() {
        RegisterRequest req = new RegisterRequest("alice@acme.com", "password123", "Alice", "Acme Corp");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(organizationRepository.existsBySlug(anyString())).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = Objects.requireNonNull(inv.<Organization>getArgument(0));
            o.setId(UUID.randomUUID());
            return o;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = Objects.requireNonNull(inv.<User>getArgument(0));
            u.setId(UUID.randomUUID());
            return u;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtService.getRefreshTokenExpirySeconds()).thenReturn(2592000L);

        AuthResponse response = authService.register(req);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.email()).isEqualTo("alice@acme.com");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void register_throwsConflict_whenEmailTaken() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("taken@acme.com", "pass", "Name", "Org")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void login_returnsTokens_onValidCredentials() {
        when(userRepository.findByEmail("alice@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(Objects.requireNonNull(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.getRefreshTokenExpirySeconds()).thenReturn(2592000L);

        AuthResponse response = authService.login(new LoginRequest("alice@acme.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_throwsUnauthorized_onWrongPassword() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@acme.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void login_throwsUnauthorized_onUnknownEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@acme.com", "pass")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void logout_deletesRefreshTokenFromRedis() {
        String userId = user.getId().toString();
        authService.logout(userId);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(keyCaptor.capture());
        assertThat(Objects.requireNonNull(keyCaptor.getValue())).isEqualTo("refresh:user:" + userId);
    }
}

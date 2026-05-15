
package com.contextengine.user.api;

import com.contextengine.user.api.dto.AuthResponse;
import com.contextengine.user.api.dto.LoginRequest;
import com.contextengine.user.api.dto.RegisterRequest;
import com.contextengine.user.model.UserRole;
import com.contextengine.user.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Objects;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;

    private AuthResponse sampleResponse() {
        return new AuthResponse(
                "access-token", "refresh-token", "Bearer", 900L,
                UUID.randomUUID(), "alice@acme.com", "Alice",
                UUID.randomUUID(), "Acme Corp", UserRole.ADMIN
        );
    }

    @Test
    void register_returns201_onValidRequest() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(sampleResponse());

        String body = Objects.requireNonNull(objectMapper.writeValueAsString(
                new RegisterRequest("alice@acme.com", "password123", "Alice", "Acme")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_returns400_onMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_onShortPassword() throws Exception {
        String body = Objects.requireNonNull(objectMapper.writeValueAsString(
                new RegisterRequest("alice@acme.com", "short", "Alice", "Acme")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200_onValidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(sampleResponse());

        String body = Objects.requireNonNull(objectMapper.writeValueAsString(
                new LoginRequest("alice@acme.com", "password123")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@acme.com"));
    }

    @Test
    void login_returns400_onInvalidEmail() throws Exception {
        String body = Objects.requireNonNull(objectMapper.writeValueAsString(
                new LoginRequest("not-an-email", "password123")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

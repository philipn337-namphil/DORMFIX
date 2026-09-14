package com.dormfix.identity.api;

import com.dormfix.platform.api.ApiError;
import com.dormfix.test.TestJwtKeys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RefreshIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        TestJwtKeys.register(registry);
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginIssuesOpaqueRefreshTokenAndRotationRevokesOldToken() {
        long userId = insertUser("refresh-owner@example.com", "ACTIVE");
        ResponseEntity<LoginResponse> login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", "refresh-owner@example.com", "password", "plain-secret"),
                LoginResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        String oldRaw = login.getBody().refreshToken();
        assertThat(oldRaw).isNotBlank();
        assertThat(login.getBody().refreshTokenExpiresAt()).isNotNull();
        byte[] oldHash = sha256(oldRaw);
        assertThat(jdbc.queryForObject(
                "SELECT token_hash FROM refresh_token_sessions WHERE user_id = ?", byte[].class, userId))
                .containsExactly(oldHash);

        ResponseEntity<TokenPairResponse> rotated = http.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", oldRaw), TokenPairResponse.class);

        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rotated.getBody()).isNotNull();
        assertThat(rotated.getBody().refreshToken()).isNotEqualTo(oldRaw);
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_token_sessions WHERE token_hash = ?",
                Boolean.class, oldHash)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, userId)).isEqualTo(1);

        ResponseEntity<ApiError> reuse = http.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", oldRaw), ApiError.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody()).isNotNull();
        assertThat(reuse.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshRejectsSuspendedUser() {
        long userId = insertUser("suspended-refresh@example.com", "ACTIVE");
        LoginResponse login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", "suspended-refresh@example.com", "password", "plain-secret"),
                LoginResponse.class).getBody();
        jdbc.update("UPDATE app_user SET status = 'SUSPENDED' WHERE id = ?", userId);

        ResponseEntity<ApiError> response = http.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", login.refreshToken()), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void logoutRevokesRefreshTokenAndSubsequentRefreshFails() {
        long userId = insertUser("logout-owner@example.com", "ACTIVE");
        LoginResponse login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", "logout-owner@example.com", "password", "plain-secret"),
                LoginResponse.class).getBody();
        String rawToken = login.refreshToken();

        ResponseEntity<Void> logout = http.postForEntity("/api/v1/auth/logout",
                Map.of("refreshToken", rawToken), Void.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_sessions WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, userId)).isEqualTo(1);

        ResponseEntity<ApiError> refresh = http.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", rawToken), ApiError.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh.getBody()).isNotNull();
        assertThat(refresh.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    private long insertUser(String email, String status) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user
                    (email, password_hash, name, status, created_at, updated_at)
                VALUES (?, ?, 'Resident', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, email, passwordEncoder.encode("plain-secret"), status);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'RESIDENT')", userId);
        return userId;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

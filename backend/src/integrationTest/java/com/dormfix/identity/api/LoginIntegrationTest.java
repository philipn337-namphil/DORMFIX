package com.dormfix.identity.api;

import com.dormfix.platform.api.ApiError;
import com.dormfix.test.TestJwtKeys;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginIntegrationTest {
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
    void loginUpdatesLastLoginAndReturnsVerifiableThirtyMinuteJwt() {
        long userId = insertUser("resident@example.com", "plain-secret", "ACTIVE");

        ResponseEntity<LoginResponse> response = http.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", " Resident@Example.COM ", "password", "plain-secret"),
                LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
        Jwt jwt = decoder().decode(response.getBody().accessToken());
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jwt.getIssuer().toString()).isEqualTo(TestJwtKeys.ISSUER);
        assertThat(jwt.getAudience()).containsExactly(TestJwtKeys.AUDIENCE);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("RESIDENT");
        assertThat(jwt.getHeaders()).containsEntry("kid", TestJwtKeys.KEY_ID);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getNotBefore()).isEqualTo(jwt.getIssuedAt());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(response.getBody().expiresAt()).isEqualTo(jwt.getExpiresAt());

        Instant lastLoginAt = jdbc.queryForObject(
                "SELECT last_login_at FROM app_user WHERE id = ?", Instant.class, userId);
        assertThat(lastLoginAt).isNotNull();
        assertThat(lastLoginAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(jwt.getIssuedAt());
    }

    @Test
    void wrongPasswordUnknownEmailAndInactiveAccountReturnSameUnauthorizedError() {
        long userId = insertUser("suspended@example.com", "plain-secret", "SUSPENDED");

        assertRejected(Map.of("email", "suspended@example.com", "password", "plain-secret"));
        assertRejected(Map.of("email", "suspended@example.com", "password", "wrong-secret"));
        assertRejected(Map.of("email", "missing@example.com", "password", "plain-secret"));
        assertThat(jdbc.queryForObject(
                "SELECT last_login_at FROM app_user WHERE id = ?", Instant.class, userId)).isNull();
    }

    private long insertUser(String email, String rawPassword, String status) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user
                    (email, password_hash, name, status, created_at, updated_at)
                VALUES (?, ?, 'Resident', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, email, passwordEncoder.encode(rawPassword), status);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'RESIDENT')", userId);
        return userId;
    }

    private void assertRejected(Map<String, String> request) {
        ResponseEntity<ApiError> response = http.postForEntity(
                "/api/v1/auth/login", request, ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().message()).isEqualTo("Email or password is invalid.");
    }

    private NimbusJwtDecoder decoder() {
        return NimbusJwtDecoder.withPublicKey(TestJwtKeys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }
}

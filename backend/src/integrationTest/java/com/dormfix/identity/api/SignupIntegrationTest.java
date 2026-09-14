package com.dormfix.identity.api;

import com.dormfix.platform.api.ApiError;
import com.dormfix.test.TestJwtKeys;
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
class SignupIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
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
    void signupPersistsNormalizedResidentWithEncodedPasswordAndRejectsDuplicate() {
        Map<String, String> request = Map.of(
                "name", "Resident",
                "email", "  New.Resident@Example.COM ",
                "password", "plain-secret",
                "phone", "010-0000-0000",
                "studentNumber", "20260001");

        ResponseEntity<SignupResponse> created = http.postForEntity(
                "/api/v1/auth/signup", request, SignupResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().email()).isEqualTo("new.resident@example.com");
        assertThat(created.getBody().status()).isEqualTo("ACTIVE");
        assertThat(created.getBody().roles()).containsExactly("RESIDENT");

        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT email, password_hash, status
                FROM app_user
                WHERE id = ?
                """, created.getBody().id());
        assertThat(stored.get("email")).isEqualTo("new.resident@example.com");
        assertThat(stored.get("password_hash")).isNotEqualTo("plain-secret");
        assertThat(passwordEncoder.matches("plain-secret", (String) stored.get("password_hash"))).isTrue();
        assertThat(stored.get("status")).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForList(
                "SELECT role FROM user_roles WHERE user_id = ?", String.class, created.getBody().id()))
                .containsExactly("RESIDENT");

        ResponseEntity<ApiError> duplicate = http.postForEntity(
                "/api/v1/auth/signup",
                Map.of("name", "Other", "email", "NEW.RESIDENT@EXAMPLE.COM", "password", "other-secret"),
                ApiError.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).isNotNull();
        assertThat(duplicate.getBody().code()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }
}

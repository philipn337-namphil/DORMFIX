package com.dormfix.identity.api;

import com.dormfix.platform.api.ApiError;
import com.dormfix.test.TestJwtKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeIntegrationTest {
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

    @Test
    void currentUserRequiresBearerAndReturnsSafeUserDto() {
        assertThat(http.getForEntity("/api/v1/me", ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        long userId = insertUser("me@example.com");
        ResponseEntity<MeResponse> response = getMe(token(userId, TestJwtKeys.ISSUER,
                TestJwtKeys.AUDIENCE, Instant.now().minusSeconds(10), Instant.now().plusSeconds(300)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(userId);
        assertThat(response.getBody().email()).isEqualTo("me@example.com");
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
        assertThat(response.getBody().roles()).containsExactly("RESIDENT");
    }

    @Test
    void invalidBearerTokensAreUnauthorized() {
        long userId = insertUser("invalid-token@example.com");
        Instant now = Instant.now();
        assertUnauthorized(token(userId, TestJwtKeys.ISSUER, TestJwtKeys.AUDIENCE,
                now.minusSeconds(300), now.minusSeconds(120)));
        assertUnauthorized(token(userId, TestJwtKeys.ISSUER, TestJwtKeys.AUDIENCE,
                now.plusSeconds(300), now.plusSeconds(600)));
        assertUnauthorized(token(userId, "https://wrong-issuer.example", TestJwtKeys.AUDIENCE,
                now.minusSeconds(1), now.plusSeconds(600)));
        assertUnauthorized(token(userId, TestJwtKeys.ISSUER, "wrong-audience",
                now.minusSeconds(1), now.plusSeconds(600)));

        String valid = token(userId, TestJwtKeys.ISSUER, TestJwtKeys.AUDIENCE,
                now.minusSeconds(1), now.plusSeconds(600));
        int signatureStart = valid.lastIndexOf('.') + 1;
        String signature = valid.substring(signatureStart);
        String tamperedSignature = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        String tampered = valid.substring(0, signatureStart) + tamperedSignature;
        assertUnauthorized(tampered);
    }

    private long insertUser(String email) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, name, status, created_at, updated_at)
                VALUES (?, '{noop}not-used', 'Resident', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, email);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'RESIDENT')", userId);
        return userId;
    }

    private ResponseEntity<MeResponse> getMe(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);
    }

    private void assertUnauthorized(String token) {
        ResponseEntity<ApiError> response = getMeError(token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private ResponseEntity<ApiError> getMeError(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);
    }

    private String token(long userId, String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("RESIDENT"))
                .build();
        RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey())
                .privateKey(TestJwtKeys.privateKey())
                .keyID(TestJwtKeys.KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(), claims))
                .getTokenValue();
    }
}

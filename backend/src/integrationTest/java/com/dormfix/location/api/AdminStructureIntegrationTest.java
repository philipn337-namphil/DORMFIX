package com.dormfix.location.api;

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
class AdminStructureIntegrationTest {
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

    @Test
    void superAdminCanCreateUpdateDeactivateAndReactivateAllThreeAggregates() {
        HttpEntity<String> superAdmin = jsonRequest("SUPER_ADMIN");
        ResponseEntity<DormitoryResponse> dormitory = http.exchange(
                "/api/v1/admin/dormitories", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Admin North\",\"address\":\"1 Main Street\","
                        + "\"timezone\":\"Asia/Seoul\"}", superAdmin.getHeaders()),
                DormitoryResponse.class);

        assertThat(dormitory.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long dormitoryId = dormitory.getBody().id();
        ResponseEntity<BuildingResponse> building = http.exchange(
                "/api/v1/admin/dormitories/{id}/buildings", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"N1\",\"name\":\"North 1\"}", superAdmin.getHeaders()),
                BuildingResponse.class, dormitoryId);
        assertThat(building.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long buildingId = building.getBody().id();
        ResponseEntity<SpaceResponse> space = http.exchange(
                "/api/v1/admin/buildings/{id}/spaces", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"101\",\"name\":\"Room 101\",\"type\":\"ROOM\","
                        + "\"floor\":1,\"description\":\"first room\"}", superAdmin.getHeaders()),
                SpaceResponse.class, buildingId);
        assertThat(space.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long spaceId = space.getBody().id();

        ResponseEntity<DormitoryResponse> updatedDormitory = http.exchange(
                "/api/v1/admin/dormitories/{id}", HttpMethod.PATCH,
                new HttpEntity<>("{\"name\":\"Admin North Updated\",\"address\":\"2 Main Street\","
                        + "\"timezone\":\"Asia/Seoul\"}", superAdmin.getHeaders()),
                DormitoryResponse.class, dormitoryId);
        assertThat(updatedDormitory.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedDormitory.getBody().name()).isEqualTo("Admin North Updated");

        assertThat(http.exchange("/api/v1/admin/spaces/{id}/deactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin.getHeaders()), SpaceResponse.class, spaceId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v1/admin/spaces/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin.getHeaders()), SpaceResponse.class, spaceId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v1/admin/buildings/{id}/deactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin.getHeaders()), BuildingResponse.class, buildingId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v1/admin/buildings/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin.getHeaders()), BuildingResponse.class, buildingId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v1/admin/dormitories/{id}/deactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin.getHeaders()), DormitoryResponse.class, dormitoryId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v1/admin/dormitories/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin.getHeaders()), DormitoryResponse.class, dormitoryId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void nonSuperAdminIsForbiddenAndActiveCannotBePatched() {
        HttpEntity<String> admin = jsonRequest("ADMIN");
        ResponseEntity<String> denied = http.exchange("/api/v1/admin/dormitories", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Denied\",\"address\":\"1 Main Street\","
                        + "\"timezone\":\"Asia/Seoul\"}", admin.getHeaders()), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        long dormitoryId = insertDormitory("Patch Guard", true);
        ResponseEntity<String> activePatch = http.exchange("/api/v1/admin/dormitories/{id}", HttpMethod.PATCH,
                new HttpEntity<>("{\"name\":\"Patch Guard\",\"address\":\"1 Main Street\","
                        + "\"timezone\":\"Asia/Seoul\",\"active\":false}", jsonRequest("SUPER_ADMIN").getHeaders()),
                String.class, dormitoryId);
        assertThat(activePatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT active FROM dormitory WHERE id = ?", Boolean.class, dormitoryId))
                .isTrue();
    }

    @Test
    void inactiveParentBlocksChildCreationAndReactivationWith409() {
        long dormitoryId = insertDormitory("Inactive Parent", false);
        ResponseEntity<String> createBuilding = http.exchange(
                "/api/v1/admin/dormitories/{id}/buildings", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"I1\",\"name\":\"Inactive 1\"}", jsonRequest("SUPER_ADMIN").getHeaders()),
                String.class, dormitoryId);
        assertThat(createBuilding.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(createBuilding.getBody()).contains("INACTIVE_PARENT");

        long buildingId = insertBuilding(dormitoryId, "I1", false);
        long spaceId = insertSpace(buildingId, "101", false);
        ResponseEntity<String> reactivateSpace = http.exchange("/api/v1/admin/spaces/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(jsonRequest("SUPER_ADMIN").getHeaders()), String.class, spaceId);
        assertThat(reactivateSpace.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reactivateSpace.getBody()).contains("INACTIVE_PARENT");
    }

    private HttpEntity<String> jsonRequest(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(role));
        headers.set("Content-Type", "application/json");
        return new HttpEntity<>(headers);
    }

    private String token(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TestJwtKeys.ISSUER)
                .audience(List.of(TestJwtKeys.AUDIENCE))
                .subject("999")
                .issuedAt(now.minusSeconds(1))
                .notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role))
                .build();
        RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey()).privateKey(TestJwtKeys.privateKey())
                .keyID(TestJwtKeys.KEY_ID).algorithm(JWSAlgorithm.RS256).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(), claims))
                .getTokenValue();
    }

    private long insertDormitory(String name, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO dormitory (name, address, timezone, active)
                VALUES (?, '1 Main Street', 'Asia/Seoul', ?)
                RETURNING id
                """, Long.class, name, active);
    }

    private long insertBuilding(long dormitoryId, String code, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO building (dormitory_id, code, name, active)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, dormitoryId, code, code + " Building", active);
    }

    private long insertSpace(long buildingId, String code, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO space (building_id, code, name, type, floor, active)
                VALUES (?, ?, ?, 'ROOM', 1, ?)
                RETURNING id
                """, Long.class, buildingId, code, "Room " + code, active);
    }
}

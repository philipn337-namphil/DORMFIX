package com.dormfix.location.api;

import com.dormfix.catalog.api.FacilityResponse;
import com.dormfix.catalog.api.MaintenanceCategoryResponse;
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
import org.springframework.core.ParameterizedTypeReference;
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
class StructureQueryIntegrationTest {
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
    void authenticatedClientCanReadAllSixStructureEndpointsAsDtos() {
        long dormitoryId = insertDormitory("North Dormitory", true);
        insertDormitory("Hidden Dormitory", false);
        long buildingId = insertBuilding(dormitoryId, "N1", true);
        insertBuilding(dormitoryId, "N2", false);
        long spaceId = insertSpace(buildingId, "101", "ROOM", true);
        insertSpace(buildingId, "102", "ROOM", false);
        insertFacility(spaceId, "Air Conditioner", "ACTIVE");
        insertFacility(spaceId, "Water Heater", "OUT_OF_SERVICE");
        long categoryId = insertCategory(null, "PLUMBING", "NORMAL", true, 1);
        insertCategory(categoryId, "WATER", "HIGH", true, 2);
        insertCategory(null, "RETIRED", "LOW", false, 3);

        HttpEntity<Void> request = authenticatedRequest();
        ResponseEntity<List<DormitoryResponse>> dormitories = http.exchange(
                "/api/v1/dormitories", HttpMethod.GET, request,
                new ParameterizedTypeReference<>() { });
        ResponseEntity<List<BuildingResponse>> buildings = http.exchange(
                "/api/v1/dormitories/{id}/buildings", HttpMethod.GET, request,
                new ParameterizedTypeReference<>() { }, dormitoryId);
        ResponseEntity<List<SpaceResponse>> spaces = http.exchange(
                "/api/v1/buildings/{id}/spaces", HttpMethod.GET, request,
                new ParameterizedTypeReference<>() { }, buildingId);
        ResponseEntity<SpaceResponse> space = http.exchange(
                "/api/v1/spaces/{id}", HttpMethod.GET, request, SpaceResponse.class, spaceId);
        ResponseEntity<List<FacilityResponse>> facilities = http.exchange(
                "/api/v1/spaces/{id}/facilities", HttpMethod.GET, request,
                new ParameterizedTypeReference<>() { }, spaceId);
        ResponseEntity<List<MaintenanceCategoryResponse>> categories = http.exchange(
                "/api/v1/categories", HttpMethod.GET, request,
                new ParameterizedTypeReference<>() { });

        assertThat(dormitories.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dormitories.getBody()).extracting(DormitoryResponse::name)
                .containsExactly("North Dormitory");
        assertThat(buildings.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(buildings.getBody()).extracting(BuildingResponse::code).containsExactly("N1");
        assertThat(spaces.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spaces.getBody()).extracting(SpaceResponse::code).containsExactly("101");
        assertThat(space.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(space.getBody()).isNotNull();
        assertThat(space.getBody().type()).isEqualTo("ROOM");
        assertThat(facilities.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(facilities.getBody()).extracting(FacilityResponse::name)
                .containsExactly("Air Conditioner", "Water Heater");
        assertThat(categories.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categories.getBody()).extracting(MaintenanceCategoryResponse::code)
                .containsExactly("PLUMBING", "WATER");
    }

    @Test
    void structureReadsRequireAuthenticationAndReturnContracted404s() {
        assertThat(http.getForEntity("/api/v1/dormitories", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpEntity<Void> request = authenticatedRequest();
        ResponseEntity<String> missingDormitory = http.exchange(
                "/api/v1/dormitories/999999/buildings", HttpMethod.GET, request, String.class);
        ResponseEntity<String> missingBuilding = http.exchange(
                "/api/v1/buildings/999999/spaces", HttpMethod.GET, request, String.class);
        ResponseEntity<String> missingSpace = http.exchange(
                "/api/v1/spaces/999999", HttpMethod.GET, request, String.class);
        ResponseEntity<String> missingFacilitySpace = http.exchange(
                "/api/v1/spaces/999999/facilities", HttpMethod.GET, request, String.class);

        assertThat(missingDormitory.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingDormitory.getBody()).contains("DORMITORY_NOT_FOUND");
        assertThat(missingBuilding.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingBuilding.getBody()).contains("BUILDING_NOT_FOUND");
        assertThat(missingSpace.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingSpace.getBody()).contains("SPACE_NOT_FOUND");
        assertThat(missingFacilitySpace.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingFacilitySpace.getBody()).contains("SPACE_NOT_FOUND");
    }

    private HttpEntity<Void> authenticatedRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token());
        return new HttpEntity<>(headers);
    }

    private String token() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TestJwtKeys.ISSUER)
                .audience(List.of(TestJwtKeys.AUDIENCE))
                .subject("999")
                .issuedAt(now.minusSeconds(1))
                .notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300))
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

    private long insertSpace(long buildingId, String code, String type, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO space (building_id, code, name, type, floor, active)
                VALUES (?, ?, ?, ?, 1, ?)
                RETURNING id
                """, Long.class, buildingId, code, "Room " + code, type, active);
    }

    private void insertFacility(long spaceId, String name, String status) {
        jdbc.update("""
                INSERT INTO facility (space_id, name, facility_type, status)
                VALUES (?, ?, 'PLUMBING', ?)
                """, spaceId, name, status);
    }

    private long insertCategory(Long parentId, String code, String priority, boolean active, int sortOrder) {
        return jdbc.queryForObject("""
                INSERT INTO maintenance_category
                    (parent_id, code, name, default_priority, active, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, parentId, code, code, priority, active, sortOrder);
    }
}

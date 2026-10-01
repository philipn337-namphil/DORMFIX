package com.dormfix.catalog.api;

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
class AdminCatalogIntegrationTest {
    private static final long ADMIN_ID = 900L;

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
    void scopedAdminCanManageFacilityButNotCategoryAndRetiredFacilityIsTerminal() {
        long spaceId = activeSpace();
        long dormitoryId = jdbc.queryForObject("SELECT b.dormitory_id FROM space s JOIN building b ON b.id = s.building_id WHERE s.id = ?",
                Long.class, spaceId);
        insertAdminScope(dormitoryId);
        HttpHeaders admin = headers(ADMIN_ID, "ADMIN");
        ResponseEntity<FacilityResponse> created = http.exchange("/api/v1/admin/spaces/{id}/facilities", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Boiler\",\"facilityType\":\"HEATING\"}", admin),
                FacilityResponse.class, spaceId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long facilityId = created.getBody().id();

        assertThat(http.exchange("/api/v1/admin/facilities/{id}/out-of-service", HttpMethod.POST,
                new HttpEntity<>(admin), FacilityResponse.class, facilityId).getBody().status())
                .isEqualTo("OUT_OF_SERVICE");
        assertThat(http.exchange("/api/v1/admin/facilities/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(admin), FacilityResponse.class, facilityId).getBody().status()).isEqualTo("ACTIVE");
        assertThat(http.exchange("/api/v1/admin/facilities/{id}/retire", HttpMethod.POST,
                new HttpEntity<>(admin), FacilityResponse.class, facilityId).getBody().status()).isEqualTo("RETIRED");
        ResponseEntity<String> terminal = http.exchange("/api/v1/admin/facilities/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(admin), String.class, facilityId);
        assertThat(terminal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(terminal.getBody()).contains("INVALID_FACILITY_STATE");

        ResponseEntity<String> categoryDenied = http.exchange("/api/v1/admin/categories", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"HVAC\",\"name\":\"HVAC\",\"defaultPriority\":\"NORMAL\",\"sortOrder\":1}", admin),
                String.class);
        assertThat(categoryDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> missingFacility = http.exchange("/api/v1/admin/facilities/{id}/retire", HttpMethod.POST,
                new HttpEntity<>(headers(901L, "SUPER_ADMIN")), String.class, 999999L);
        assertThat(missingFacility.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingFacility.getBody()).contains("FACILITY_NOT_FOUND");
    }

    @Test
    void categoryParentIsCreateOnlyAndInactiveParentBlocksCreationAndReactivation() {
        HttpHeaders superAdmin = headers(901L, "SUPER_ADMIN");
        long parentId = createCategory(superAdmin, "ROOT", "Root");
        long childId = createCategory(superAdmin, "CHILD", "Child", parentId);
        assertThat(http.exchange("/api/v1/admin/categories/{id}/deactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin), MaintenanceCategoryResponse.class, parentId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<String> blockedCreate = http.exchange("/api/v1/admin/categories", HttpMethod.POST,
                new HttpEntity<>("{\"parentId\":" + parentId + ",\"code\":\"NEW\",\"name\":\"New\","
                        + "\"defaultPriority\":\"NORMAL\",\"sortOrder\":3}", superAdmin), String.class);
        assertThat(blockedCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blockedCreate.getBody()).contains("INACTIVE_PARENT");
        assertThat(http.exchange("/api/v1/admin/categories/{id}/deactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin), MaintenanceCategoryResponse.class, childId).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> blockedReactivate = http.exchange("/api/v1/admin/categories/{id}/reactivate", HttpMethod.POST,
                new HttpEntity<>(superAdmin), String.class, childId);
        assertThat(blockedReactivate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blockedReactivate.getBody()).contains("INACTIVE_PARENT");
        ResponseEntity<String> parentInPatch = http.exchange("/api/v1/admin/categories/{id}", HttpMethod.PATCH,
                new HttpEntity<>("{\"parentId\":null,\"code\":\"CHILD\",\"name\":\"Child\","
                        + "\"defaultPriority\":\"NORMAL\",\"sortOrder\":2}", superAdmin), String.class, childId);
        assertThat(parentInPatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private long createCategory(HttpHeaders headers, String code, String name) {
        return createCategory(headers, code, name, null);
    }

    private long createCategory(HttpHeaders headers, String code, String name, Long parentId) {
        String parent = parentId == null ? "null" : parentId.toString();
        ResponseEntity<MaintenanceCategoryResponse> response = http.exchange("/api/v1/admin/categories", HttpMethod.POST,
                new HttpEntity<>("{\"parentId\":" + parent + ",\"code\":\"" + code + "\",\"name\":\""
                        + name + "\",\"defaultPriority\":\"NORMAL\",\"sortOrder\":1}", headers),
                MaintenanceCategoryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private long activeSpace() {
        long dormitoryId = jdbc.queryForObject("INSERT INTO dormitory (name, address, timezone) VALUES ('Catalog', '1 Main', 'Asia/Seoul') RETURNING id", Long.class);
        long buildingId = jdbc.queryForObject("INSERT INTO building (dormitory_id, code, name) VALUES (?, 'C1', 'Catalog 1') RETURNING id", Long.class, dormitoryId);
        return jdbc.queryForObject("INSERT INTO space (building_id, code, name, type, floor) VALUES (?, '101', 'Room 101', 'ROOM', 1) RETURNING id", Long.class, buildingId);
    }

    private void insertAdminScope(long dormitoryId) {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, name, status, created_at, updated_at) "
                + "VALUES (?, 'admin@example.test', 'hash', 'Admin', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ADMIN_ID);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'ADMIN')", ADMIN_ID);
        jdbc.update("INSERT INTO admin_dormitory_scopes (user_id, dormitory_id) VALUES (?, ?)", ADMIN_ID, dormitoryId);
    }

    private HttpHeaders headers(long subject, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(subject, role));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private String token(long subject, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TestJwtKeys.ISSUER).audience(List.of(TestJwtKeys.AUDIENCE))
                .subject(Long.toString(subject)).issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300)).claim("roles", List.of(role)).build();
        RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey()).privateKey(TestJwtKeys.privateKey())
                .keyID(TestJwtKeys.KEY_ID).algorithm(JWSAlgorithm.RS256).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(TestJwtKeys.KEY_ID).build(), claims)).getTokenValue();
    }
}

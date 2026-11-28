package com.dormfix.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.test.TestJwtKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** ADR-030: GET /admin/users. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        TestJwtKeys.register(registry);
    }

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void superAdminListsEveryoneAndFiltersWithoutLeakingPersonalFields() throws Exception {
        String tag = key();
        long resident = user("RESIDENT", "Resident " + tag, "res-" + tag + "@test");
        jdbc.update("update app_user set status='SUSPENDED' where id=?", resident);
        HttpHeaders root = headers(user("SUPER_ADMIN"), "SUPER_ADMIN");

        // Already encoded on purpose: the spaces must reach the server to prove the trim.
        ResponseEntity<String> byEmail = http.exchange(java.net.URI.create(http.getRootUri()
                + "/api/v1/admin/users?email=%20RES-" + tag + "@TEST%20"), HttpMethod.GET,
                new HttpEntity<>(root), String.class);
        assertThat(byEmail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = JSON.readTree(byEmail.getBody());
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        JsonNode row = page.get("content").get(0);
        assertThat(row.get("id").asLong()).isEqualTo(resident);
        assertThat(row.get("roles").toString()).isEqualTo("[\"RESIDENT\"]");
        assertThat(row.get("status").asText()).isEqualTo("SUSPENDED");
        java.util.List<String> fields = new java.util.ArrayList<>();
        row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "email", "roles", "status");
        assertThat(byEmail.getBody()).doesNotContain("010-1234-5678").doesNotContain("password");

        assertThat(JSON.readTree(get("/api/v1/admin/users?role=RESIDENT&status=SUSPENDED&size=100",
                root).getBody()).toString()).contains("res-" + tag);
        assertThat(JSON.readTree(get("/api/v1/admin/users?role=WORKER&size=100", root).getBody())
                .toString()).doesNotContain("res-" + tag);
    }

    @Test
    void adminListsWorkersByNameAndNeverResidentsThatWay() throws Exception {
        String tag = key();
        long later = user("WORKER", "zz worker " + tag, "w2-" + tag + "@test");
        long earlier = user("WORKER", "aa worker " + tag, "w1-" + tag + "@test");
        user("RESIDENT", "aa resident " + tag, "r-" + tag + "@test");
        HttpHeaders admin = headers(user("ADMIN"), "ADMIN");

        ResponseEntity<String> workers = get("/api/v1/admin/users?role=WORKER&size=100", admin);

        assertThat(workers.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = workers.getBody();
        assertThat(body).doesNotContain("r-" + tag).doesNotContain("\"RESIDENT\"");
        assertThat(body.indexOf("\"id\":" + earlier)).isLessThan(body.indexOf("\"id\":" + later));
    }

    @Test
    void adminFindsOneResidentOnlyByExactEmail() throws Exception {
        String tag = key();
        long resident = user("RESIDENT", "Resident " + tag, "exact-" + tag + "@test");
        HttpHeaders admin = headers(user("ADMIN"), "ADMIN");

        JsonNode found = JSON.readTree(get("/api/v1/admin/users?role=RESIDENT&email=exact-" + tag
                + "@test", admin).getBody());
        assertThat(found.get("totalElements").asLong()).isEqualTo(1);
        assertThat(found.get("content").get(0).get("id").asLong()).isEqualTo(resident);

        JsonNode partial = JSON.readTree(get("/api/v1/admin/users?role=RESIDENT&email=exact-"
                + tag, admin).getBody());
        assertThat(partial.get("totalElements").asLong()).isZero();
    }

    @Test
    void everyOtherCallerAndRequestIsRejected() {
        HttpHeaders admin = headers(user("ADMIN"), "ADMIN");
        for (String query : java.util.List.of("", "?role=RESIDENT", "?role=ADMIN",
                "?role=SUPER_ADMIN&email=a@test")) {
            ResponseEntity<String> response = get("/api/v1/admin/users" + query, admin);
            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).contains("ACCESS_DENIED");
        }
        for (String role : java.util.List.of("RESIDENT", "WORKER")) {
            assertThat(get("/api/v1/admin/users?role=WORKER", headers(user(role), role))
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(http.getForEntity("/api/v1/admin/users", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidFiltersAndPaginationAreValidationErrors() {
        HttpHeaders root = headers(user("SUPER_ADMIN"), "SUPER_ADMIN");
        for (String query : java.util.List.of("?role=JANITOR", "?status=GONE", "?size=0",
                "?size=101", "?page=-1", "?page=x")) {
            ResponseEntity<String> response = get("/api/v1/admin/users" + query, root);
            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("VALIDATION_FAILED");
        }
    }

    private long user(String role, String name, String email) {
        long id = jdbc.queryForObject(
                "insert into app_user(email,password_hash,name,phone,student_number,status,"
                        + "created_at,updated_at) values (?,'h',?,'010-1234-5678',?,'ACTIVE',"
                        + "current_timestamp,current_timestamp) returning id",
                Long.class, email, name, key());
        jdbc.update("insert into user_roles(user_id,role) values (?,?)", id, role);
        return id;
    }

    private long user(String role) {
        String name = key();
        return user(role, name, name + "@test");
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private long dormitory() {
        String name = key();
        return jdbc.queryForObject(
                "insert into dormitory(name,address,timezone) values (?,?,'Asia/Seoul') returning id",
                Long.class, name, name);
    }

    private long space(long dormitory) {
        long building = jdbc.queryForObject(
                "insert into building(dormitory_id,code,name) values (?,?,?) returning id",
                Long.class, dormitory, key(), "B");
        return jdbc.queryForObject(
                "insert into space(building_id,code,name,type,floor) values (?,?,?,'ROOM',1) returning id",
                Long.class, building, key(), "R");
    }

    private String key() {
        return UUID.randomUUID().toString().substring(0, 20);
    }

    private HttpHeaders headers(long id, String... roles) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token(id, List.of(roles)));
        return headers;
    }

    private String token(long id, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TestJwtKeys.ISSUER)
                .audience(List.of(TestJwtKeys.AUDIENCE)).subject(Long.toString(id))
                .issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300)).claim("roles", roles).build();
        RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey())
                .privateKey(TestJwtKeys.privateKey()).keyID(TestJwtKeys.KEY_ID)
                .algorithm(JWSAlgorithm.RS256).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(), claims))
                .getTokenValue();
    }

}

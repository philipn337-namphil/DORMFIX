package com.dormfix.maintenance.api;

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

/** ADR-030: GET /admin/dashboard/summary. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminDashboardIntegrationTest {
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
    void anAdminCountsItsScopeOnly() throws Exception {
        Fixture f = fixture();

        ResponseEntity<String> response = get("/api/v1/admin/dashboard/summary", f.admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode summary = JSON.readTree(response.getBody());
        assertThat(summary.get("dormitoryIds").toString()).isEqualTo("[" + f.scoped() + "]");
        JsonNode statuses = summary.get("statusCounts");
        assertThat(statuses.size()).isEqualTo(8);
        assertThat(statuses.get("REPORTED").asLong()).isEqualTo(1);
        assertThat(statuses.get("IN_PROGRESS").asLong()).isEqualTo(1);
        assertThat(statuses.get("CLOSED").asLong()).isEqualTo(1);
        assertThat(statuses.get("REJECTED").asLong()).isEqualTo(1);
        assertThat(statuses.get("ASSIGNED").asLong()).isZero();
        JsonNode priorities = summary.get("activePriorityCounts");
        assertThat(priorities.size()).isEqualTo(4);
        assertThat(priorities.get("HIGH").asLong()).isEqualTo(1);
        assertThat(priorities.get("EMERGENCY").asLong()).isEqualTo(1);
        // The closed LOW and the rejected NORMAL requests are terminal and not active.
        assertThat(priorities.get("LOW").asLong()).isZero();
        assertThat(priorities.get("NORMAL").asLong()).isZero();
        assertThat(summary.get("createdLast7Days").asLong()).isEqualTo(3);
        assertThat(summary.get("closedLast7Days").asLong()).isEqualTo(1);
        assertThat(summary.has("generatedAt")).isTrue();
        assertThat(response.getBody()).doesNotContain("requestNumber");
    }

    @Test
    void anAdminNamingADormitoryOutsideScopeIsDeniedEvenIfItDoesNotExist() {
        Fixture f = fixture();
        for (String id : java.util.List.of(Long.toString(f.other()), "999999999")) {
            ResponseEntity<String> response = get("/api/v1/admin/dashboard/summary?dormitoryId="
                    + id, f.admin());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).contains("ACCESS_DENIED");
        }
        assertThat(get("/api/v1/admin/dashboard/summary?dormitoryId=" + f.scoped(), f.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/admin/dashboard/summary", headers(user("ADMIN"), "ADMIN"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void superAdminCountsOneDormitoryOrEverythingAndAMissingOneIsNotFound() throws Exception {
        Fixture f = fixture();
        HttpHeaders root = headers(user("SUPER_ADMIN"), "SUPER_ADMIN");

        JsonNode other = JSON.readTree(get("/api/v1/admin/dashboard/summary?dormitoryId="
                + f.other(), root).getBody());
        assertThat(other.get("statusCounts").get("REPORTED").asLong()).isEqualTo(1);
        assertThat(other.get("dormitoryIds").toString()).isEqualTo("[" + f.other() + "]");

        JsonNode all = JSON.readTree(get("/api/v1/admin/dashboard/summary", root).getBody());
        assertThat(all.get("dormitoryIds").toString()).contains(Long.toString(f.scoped()))
                .contains(Long.toString(f.other()));

        ResponseEntity<String> missing = get("/api/v1/admin/dashboard/summary?dormitoryId="
                + "999999999", root);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("DORMITORY_NOT_FOUND");
    }

    @Test
    void nonAdministratorsAndBadParametersAreRejected() {
        for (String role : java.util.List.of("RESIDENT", "WORKER")) {
            assertThat(get("/api/v1/admin/dashboard/summary", headers(user(role), role))
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        ResponseEntity<String> bad = get("/api/v1/admin/dashboard/summary?dormitoryId=x",
                headers(user("SUPER_ADMIN"), "SUPER_ADMIN"));
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).contains("VALIDATION_FAILED");
        assertThat(http.getForEntity("/api/v1/admin/dashboard/summary", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Scoped dormitory: REPORTED HIGH, IN_PROGRESS EMERGENCY, CLOSED LOW (closed a day ago) and a
     * REJECTED NORMAL created ten days ago. The other dormitory: one REPORTED request.
     */
    private Fixture fixture() {
        long scoped = dormitory();
        long other = dormitory();
        long admin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)", admin,
                scoped);
        long reporter = user("RESIDENT");
        long category = jdbc.queryForObject(
                "insert into maintenance_category(code,name,default_priority,sort_order)"
                        + " values (?,?,'HIGH',1) returning id", Long.class, key(), "Leaks");
        long room = space(scoped);
        request(reporter, room, category, "REPORTED", "HIGH", "now()", null);
        request(reporter, room, category, "IN_PROGRESS", "EMERGENCY", "now()", null);
        request(reporter, room, category, "CLOSED", "LOW", "now() - interval '2 days'",
                "now() - interval '1 day'");
        request(reporter, room, category, "REJECTED", "NORMAL", "now() - interval '10 days'", null);
        request(reporter, space(other), category, "REPORTED", "NORMAL", "now()", null);
        return new Fixture(scoped, other, headers(admin, "ADMIN"));
    }

    private void request(long reporter, long room, long category, String status, String priority,
            String createdAt, String closedAt) {
        String closed = closedAt == null ? "null" : closedAt;
        jdbc.update("insert into maintenance_request(request_number,reporter_id,space_id,category_id,"
                + "title,description,priority,status,entry_policy,contact_before_entry,created_at,"
                + "resolved_at,closed_at) values (?,?,?,?,'Leak','Ceiling leak',?,?,"
                + "'RESIDENT_PRESENT_REQUIRED',true," + createdAt + "," + closed + "," + closed + ")",
                "P-" + key(), reporter, room, category, priority, status);
    }

    private record Fixture(long scoped, long other, HttpHeaders admin) {
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

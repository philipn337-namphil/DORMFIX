package com.dormfix.location.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.test.TestJwtKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResidenceIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createRejectsUnknownEndDateFutureAndSameDayEnd() {
        Fixture fixture = fixture();
        String unknown = payload(fixture.residentId(), fixture.roomId(), today())
                .replace("}", ",\"endDate\":\"2026-01-01\"}");
        assertStatus(HttpMethod.POST, "/api/v1/admin/residences", unknown, fixture.adminHeaders(), HttpStatus.BAD_REQUEST);
        assertStatus(HttpMethod.POST, "/api/v1/admin/residences", payload(fixture.residentId(), fixture.roomId(), today().plusDays(1)), fixture.adminHeaders(), HttpStatus.BAD_REQUEST);
        ResidenceResponse created = create(fixture, fixture.roomId(), today());
        ResponseEntity<String> ended = exchange(HttpMethod.POST, "/api/v1/admin/residences/" + created.id() + "/end", null, fixture.adminHeaders());
        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ended.getBody()).contains("RESIDENCE_SAME_DAY_END");
    }

    @Test
    void endThenCreateSameDayAndOverlapDatabaseDefenceWork() {
        Fixture fixture = fixture();
        ResidenceResponse old = create(fixture, fixture.roomId(), today().minusDays(2));
        assertThatThrownBy(() -> jdbc.update("insert into residence(resident_id,room_space_id,start_date) values (?,?,?)",
                fixture.residentId(), fixture.roomId(), java.sql.Date.valueOf(today().minusDays(1))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        ResponseEntity<String> overlap = exchange(HttpMethod.POST, "/api/v1/admin/residences",
                payload(fixture.residentId(), fixture.roomId(), today().minusDays(1)), fixture.adminHeaders());
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overlap.getBody()).contains("RESIDENCE_PERIOD_OVERLAP");
        assertThat(exchange(HttpMethod.POST, "/api/v1/admin/residences/" + old.id() + "/end", null, fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(create(fixture, fixture.otherRoomId(), today()).startDate()).isEqualTo(today());
    }

    @Test
    void permitsMultipleResidentsButRejectsNonRoomInactiveAndMissingTargets() {
        Fixture fixture = fixture();
        long second = user("RESIDENT");
        create(fixture, fixture.roomId(), today());
        assertThat(create(fixture, fixture.roomId(), second, today()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long nonRoom = space(fixture.dormitoryId(), "LOUNGE", true);
        assertConflict(fixture, fixture.residentId(), nonRoom, "SPACE_NOT_ROOM");
        long inactive = space(fixture.dormitoryId(), "ROOM", false);
        assertConflict(fixture, fixture.residentId(), inactive, "INACTIVE_PARENT");
        assertThat(exchange(HttpMethod.POST, "/api/v1/admin/residences", payload(999999L, fixture.roomId(), today()), fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/admin/residences", payload(fixture.residentId(), 999999L, today()), fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.GET, "/api/v1/admin/residences/999999", null, fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void scopeAndRoleRulesProtectCreateDetailEndAndList() {
        Fixture fixture = fixture();
        ResidenceResponse residence = create(fixture, fixture.roomId(), today().minusDays(2));
        Fixture other = fixture();
        assertThat(exchange(HttpMethod.POST, "/api/v1/admin/residences", payload(fixture.residentId(), other.roomId(), today()), fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET, "/api/v1/admin/residences/" + residence.id(), null, other.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/admin/residences/" + residence.id() + "/end", null, other.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET, "/api/v1/admin/residences?roomSpaceId=" + other.roomId(), null, fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        long noScope = user("ADMIN");
        assertThat(exchange(HttpMethod.GET, "/api/v1/admin/residences?current=true", null, headers(noScope, "ADMIN")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> superAdmin = exchange(HttpMethod.GET, "/api/v1/admin/residences?current=true", null, headers(user("SUPER_ADMIN"), "SUPER_ADMIN"));
        assertThat(superAdmin.getStatusCode()).withFailMessage("response body: %s", superAdmin.getBody()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void selfListPaginationCurrentFilterAndAdminFilterValidationWork() {
        Fixture fixture = fixture();
        ResidenceResponse first = create(fixture, fixture.roomId(), today().minusDays(3));
        assertThat(exchange(HttpMethod.POST, "/api/v1/admin/residences/" + first.id() + "/end", null, fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResidenceResponse current = create(fixture, fixture.otherRoomId(), today());
        ResponseEntity<String> own = exchange(HttpMethod.GET, "/api/v1/me/residences?current=true&page=0&size=1", null, headers(fixture.residentId(), "RESIDENT"));
        assertThat(own.getStatusCode()).withFailMessage("response body: %s", own.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(own.getBody()).contains("\"id\":" + current.id()).doesNotContain("\"id\":" + first.id());
        assertThat(exchange(HttpMethod.GET, "/api/v1/me/residences?page=-1", null, headers(fixture.residentId(), "RESIDENT")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange(HttpMethod.GET, "/api/v1/me/residences?size=101", null, headers(fixture.residentId(), "RESIDENT")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange(HttpMethod.GET, "/api/v1/me/residences", null, headers(user("WORKER"), "WORKER")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET, "/api/v1/admin/residences", null, fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange(HttpMethod.GET, "/api/v1/admin/residences?current=true&page=0&size=101", null, fixture.adminHeaders()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void assertConflict(Fixture fixture, long resident, long room, String code) {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/admin/residences", payload(resident, room, today()), fixture.adminHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains(code);
    }

    private ResidenceResponse create(Fixture fixture, long room, LocalDate start) { return create(fixture, room, fixture.residentId(), start).getBody(); }
    private ResponseEntity<ResidenceResponse> create(Fixture fixture, long room, long resident, LocalDate start) {
        return http.exchange("/api/v1/admin/residences", HttpMethod.POST, new HttpEntity<>(payload(resident, room, start), fixture.adminHeaders()), ResidenceResponse.class);
    }
    private void assertStatus(HttpMethod method, String url, String body, HttpHeaders headers, HttpStatus status) { assertThat(exchange(method, url, body, headers).getStatusCode()).isEqualTo(status); }
    private ResponseEntity<String> exchange(HttpMethod method, String url, String body, HttpHeaders headers) { return http.exchange(url, method, new HttpEntity<>(body, headers), String.class); }
    private LocalDate today() { return LocalDate.now(SEOUL); }
    private String payload(long resident, long room, LocalDate start) { return "{\"residentId\":" + resident + ",\"roomSpaceId\":" + room + ",\"startDate\":\"" + start + "\"}"; }

    private Fixture fixture() { long dorm = dormitory(); long room = space(dorm, "ROOM", true); long second = space(dorm, "ROOM", true); long resident = user("RESIDENT"); long admin = user("ADMIN"); jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)", admin, dorm); return new Fixture(dorm, room, second, resident, admin, headers(admin, "ADMIN")); }
    private long dormitory() { String value = UUID.randomUUID().toString(); return jdbc.queryForObject("insert into dormitory(name,address,timezone) values (?,?,'Asia/Seoul') returning id", Long.class, value, value); }
    private long space(long dormitory, String type, boolean active) { long building = jdbc.queryForObject("insert into building(dormitory_id,code,name,active) values (?,?,?,?) returning id", Long.class, dormitory, key(), "B", active); return jdbc.queryForObject("insert into space(building_id,code,name,type,floor,active) values (?,?,?,?,?,?) returning id", Long.class, building, key(), "S", type, 1, active); }
    private String key() { return UUID.randomUUID().toString().substring(0, 20); }
    private long user(String role) { String value = UUID.randomUUID().toString(); long id = jdbc.queryForObject("insert into app_user(email,password_hash,name,status,created_at,updated_at) values (?,'h',?,'ACTIVE',current_timestamp,current_timestamp) returning id", Long.class, value + "@test", value); jdbc.update("insert into user_roles(user_id,role) values (?,?)", id, role); return id; }
    private HttpHeaders headers(long id, String role) { HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token(id, role)); headers.setContentType(MediaType.APPLICATION_JSON); return headers; }
    private String token(long id, String role) { Instant now = Instant.now(); JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TestJwtKeys.ISSUER).audience(List.of(TestJwtKeys.AUDIENCE)).subject(Long.toString(id)).issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).claim("roles", List.of(role)).build(); RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey()).privateKey(TestJwtKeys.privateKey()).keyID(TestJwtKeys.KEY_ID).algorithm(JWSAlgorithm.RS256).build(); JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))); return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(), claims)).getTokenValue(); }

    private record Fixture(long dormitoryId, long roomId, long otherRoomId, long residentId, long adminId, HttpHeaders adminHeaders) { }
}

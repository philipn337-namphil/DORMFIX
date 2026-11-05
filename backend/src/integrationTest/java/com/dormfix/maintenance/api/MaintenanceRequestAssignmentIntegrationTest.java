package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.dormfix.maintenance.application.RequestHistoryRepository;
import com.dormfix.maintenance.domain.RequestHistory;
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
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaintenanceRequestAssignmentIntegrationTest {
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
    @MockitoSpyBean
    RequestHistoryRepository historyRepository;

    @Test
    void superAdminAssignsAReportedRequestAndTheAuditRecordsIt() {
        Fixture fixture = fixture();
        long worker = user("WORKER");

        ResponseEntity<String> response = assign(fixture.request(), worker, 0L, fixture.superAdmin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ASSIGNED");
        assertThat(status(fixture.request())).isEqualTo("ASSIGNED");
        assertThat(activeWorker(fixture.request())).isEqualTo(worker);
        assertThat(jdbc.queryForObject(
                "select count(*) from request_history where request_id=? and event_type='REQUEST_ASSIGNED'"
                        + " and previous_status='REPORTED' and new_status='ASSIGNED'",
                Integer.class, fixture.request())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select metadata->>'workerId' from request_history where request_id=?"
                        + " and event_type='REQUEST_ASSIGNED'",
                String.class, fixture.request())).isEqualTo(Long.toString(worker));
    }

    @Test
    void reassignmentEndsThePreviousAssignmentAndKeepsOneActiveRow() {
        Fixture fixture = fixture();
        long first = user("WORKER");
        long second = user("WORKER");
        assign(fixture.request(), first, 0L, fixture.superAdmin());

        ResponseEntity<String> response =
                assign(fixture.request(), second, version(fixture.request()), fixture.superAdmin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(fixture.request())).isEqualTo("ASSIGNED");
        assertThat(activeWorker(fixture.request())).isEqualTo(second);
        assertThat(jdbc.queryForObject(
                "select count(*) from maintenance_assignment where request_id=? and unassigned_at is null",
                Integer.class, fixture.request())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from maintenance_assignment where request_id=? and worker_id=?"
                        + " and unassigned_at is not null and unassigned_by_id=?",
                Integer.class, fixture.request(), first, fixture.superAdminId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select metadata->>'previousWorkerId' from request_history where request_id=?"
                        + " and event_type='REQUEST_REASSIGNED'",
                String.class, fixture.request())).isEqualTo(Long.toString(first));
    }

    @Test
    void reassignmentFromInProgressReturnsTheRequestToAssigned() {
        Fixture fixture = fixture();
        long first = user("WORKER");
        long second = user("WORKER");
        assign(fixture.request(), first, 0L, fixture.superAdmin());
        jdbc.update("update maintenance_request set status='IN_PROGRESS' where id=?",
                fixture.request());

        assign(fixture.request(), second, version(fixture.request()), fixture.superAdmin());

        assertThat(status(fixture.request())).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject(
                "select previous_status from request_history where request_id=?"
                        + " and event_type='REQUEST_REASSIGNED'",
                String.class, fixture.request())).isEqualTo("IN_PROGRESS");
    }

    @Test
    void scopedAdminMayAssignWhileOutOfScopeAndScopelessAdminsAndResidentsAreForbidden() {
        Fixture fixture = fixture();
        long worker = user("WORKER");
        long scopedAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                scopedAdmin, fixture.dormitory());
        long otherAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                otherAdmin, dormitory());

        assertThat(assign(fixture.request(), worker, 0L, headers(scopedAdmin, "ADMIN"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertForbidden(assign(fixture.request(), worker, version(fixture.request()),
                headers(otherAdmin, "ADMIN")));
        assertForbidden(assign(fixture.request(), worker, version(fixture.request()),
                headers(user("ADMIN"), "ADMIN")));
        assertForbidden(assign(fixture.request(), worker, version(fixture.request()),
                headers(fixture.resident(), "RESIDENT")));
    }

    @Test
    void anUnknownWorkerIs404AndANonWorkerOrInactiveWorkerIs409() {
        Fixture fixture = fixture();

        ResponseEntity<String> missing = assign(fixture.request(), 999999L, 0L, fixture.superAdmin());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("WORKER_NOT_FOUND");

        ResponseEntity<String> notAWorker =
                assign(fixture.request(), user("RESIDENT"), 0L, fixture.superAdmin());
        assertThat(notAWorker.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notAWorker.getBody()).contains("INVALID_WORKER");

        long suspended = user("WORKER");
        jdbc.update("update app_user set status='SUSPENDED' where id=?", suspended);
        ResponseEntity<String> inactive = assign(fixture.request(), suspended, 0L, fixture.superAdmin());
        assertThat(inactive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inactive.getBody()).contains("INVALID_WORKER");
    }

    @Test
    void assigningTheCurrentAssigneeAgainIsAlreadyAssignedAndNotAVersionConflict() {
        Fixture fixture = fixture();
        long worker = user("WORKER");
        assign(fixture.request(), worker, 0L, fixture.superAdmin());

        ResponseEntity<String> response =
                assign(fixture.request(), worker, version(fixture.request()), fixture.superAdmin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("REQUEST_ALREADY_ASSIGNED")
                .doesNotContain("VERSION_CONFLICT");
        assertThat(status(fixture.request())).isEqualTo("ASSIGNED");
    }

    @Test
    void aStaleVersionIsAVersionConflictAndChangesNothing() {
        Fixture fixture = fixture();
        long worker = user("WORKER");

        ResponseEntity<String> response = assign(fixture.request(), worker, 99L, fixture.superAdmin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("VERSION_CONFLICT");
        assertThat(status(fixture.request())).isEqualTo("REPORTED");
        assertThat(jdbc.queryForObject("select count(*) from maintenance_assignment where request_id=?",
                Integer.class, fixture.request())).isZero();
    }

    @Test
    void resolvedAndTerminalStatesCannotBeAssigned() {
        for (String blocked : List.of("RESOLVED", "CLOSED", "REJECTED", "DUPLICATE")) {
            Fixture fixture = fixture();
            if ("DUPLICATE".equals(blocked)) {
                // ck_maintenance_request_duplicate_status requires the original, so a DUPLICATE
                // row cannot be forged without one.
                long original = request(fixture.resident(), fixture.room());
                jdbc.update("update maintenance_request set status=?, duplicate_of_id=? where id=?",
                        blocked, original, fixture.request());
            } else {
                jdbc.update("update maintenance_request set status=? where id=?", blocked,
                        fixture.request());
            }

            ResponseEntity<String> response = assign(fixture.request(), user("WORKER"),
                    version(fixture.request()), fixture.superAdmin());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).contains("INVALID_REQUEST_STATE");
            assertThat(status(fixture.request())).isEqualTo(blocked);
        }
    }

    @Test
    void theDatabaseItselfRefusesASecondActiveAssignment() {
        Fixture fixture = fixture();
        long worker = user("WORKER");
        assign(fixture.request(), worker, 0L, fixture.superAdmin());

        assertThat(org.assertj.core.api.Assertions
                .catchThrowable(() -> jdbc.update(
                        "insert into maintenance_assignment(request_id,worker_id,assigned_by_id)"
                                + " values (?,?,?)",
                        fixture.request(), user("WORKER"), fixture.superAdminId())))
                .isNotNull();
    }

    @Test
    void assignmentAndItsHistoryRollBackTogether() {
        Fixture fixture = fixture();
        long worker = user("WORKER");
        doThrow(new IllegalStateException("history write failed")).when(historyRepository)
                .save(any(RequestHistory.class));

        ResponseEntity<String> response = assign(fixture.request(), worker, 0L, fixture.superAdmin());

        assertThat(response.getStatusCode().isError()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from maintenance_assignment where request_id=?",
                Integer.class, fixture.request())).isZero();
        assertThat(status(fixture.request())).isEqualTo("REPORTED");
    }

    private void assertForbidden(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
    }

    private ResponseEntity<String> assign(long request, long worker, long version,
            HttpHeaders headers) {
        String body = "{\"workerId\":" + worker + ",\"version\":" + version + "}";
        return http.exchange("/api/v1/maintenance-requests/" + request + "/assignments",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String status(long request) {
        return jdbc.queryForObject("select status from maintenance_request where id=?", String.class,
                request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private Long activeWorker(long request) {
        return jdbc.queryForObject(
                "select worker_id from maintenance_assignment where request_id=? and unassigned_at is null",
                Long.class, request);
    }

    private Fixture fixture() {
        long dormitory = dormitory();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        jdbc.update("insert into residence(resident_id,room_space_id,start_date) values (?,?,?)",
                resident, room,
                java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)));
        long request = request(resident, room);
        long superAdmin = user("SUPER_ADMIN");
        return new Fixture(dormitory, room, resident, request, superAdmin,
                headers(superAdmin, "SUPER_ADMIN"));
    }

    private long request(long reporter, long room) {
        long category = jdbc.queryForObject(
                "insert into maintenance_category(code,name,default_priority,sort_order)"
                        + " values (?,?,'HIGH',1) returning id",
                Long.class, key(), "Leaks");
        long id = jdbc.queryForObject(
                "insert into maintenance_request(request_number,reporter_id,space_id,category_id,title,"
                        + "description,priority,status,entry_policy,contact_before_entry)"
                        + " values (?,?,?,?,'Leak','Ceiling leak','HIGH','REPORTED',"
                        + "'RESIDENT_PRESENT_REQUIRED',true) returning id",
                Long.class, "P-" + key(), reporter, room, category);
        jdbc.update("update maintenance_request set request_number=? where id=?", "DF-" + id, id);
        return id;
    }

    private long dormitory() {
        String name = key();
        return jdbc.queryForObject(
                "insert into dormitory(name,address,timezone) values (?,?,'Asia/Seoul') returning id",
                Long.class, name, name);
    }

    private long space(long dormitory) {
        long building = jdbc.queryForObject(
                "insert into building(dormitory_id,code,name) values (?,?,?) returning id", Long.class,
                dormitory, key(), "B");
        return jdbc.queryForObject(
                "insert into space(building_id,code,name,type,floor) values (?,?,?,'ROOM',1) returning id",
                Long.class, building, key(), "R");
    }

    private long user(String role) {
        String name = key();
        long id = jdbc.queryForObject(
                "insert into app_user(email,password_hash,name,status,created_at,updated_at)"
                        + " values (?,'h',?,'ACTIVE',current_timestamp,current_timestamp) returning id",
                Long.class, name + "@test", name);
        jdbc.update("insert into user_roles(user_id,role) values (?,?)", id, role);
        return id;
    }

    private String key() {
        return UUID.randomUUID().toString().substring(0, 20);
    }

    private HttpHeaders headers(long id, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token(id, role));
        return headers;
    }

    private String token(long id, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TestJwtKeys.ISSUER)
                .audience(List.of(TestJwtKeys.AUDIENCE)).subject(Long.toString(id))
                .issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300)).claim("roles", List.of(role)).build();
        RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey())
                .privateKey(TestJwtKeys.privateKey()).keyID(TestJwtKeys.KEY_ID)
                .algorithm(JWSAlgorithm.RS256).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(), claims))
                .getTokenValue();
    }

    private record Fixture(long dormitory, long room, long resident, long request, long superAdminId,
            HttpHeaders superAdmin) {
    }
}

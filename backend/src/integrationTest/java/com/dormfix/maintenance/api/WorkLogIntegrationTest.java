package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.test.TestJwtKeys;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkLogIntegrationTest {
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
    void theAssigneeAppendsAWorkLogAndTheServerOwnsTheWorkerAndTime() {
        Scene scene = assigned();

        ResponseEntity<String> response = append(scene.request(),
                "{\"actionType\":\"REPAIR\",\"description\":\"replaced the trap\"}",
                scene.worker());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("REPAIR").contains("replaced the trap");
        assertThat(jdbc.queryForObject("select worker_id from work_log where request_id=?",
                Long.class, scene.request())).isEqualTo(scene.workerId());
        assertThat(jdbc.queryForObject("select created_at is not null from work_log where request_id=?",
                Boolean.class, scene.request())).isTrue();
    }

    @Test
    void appendingAWorkLogLeavesTheRequestAndItsHistoryUntouched() {
        Scene scene = assigned();
        long before = jdbc.queryForObject("select count(*) from request_history where request_id=?",
                Long.class, scene.request());
        long version = version(scene.request());

        append(scene.request(), "{\"actionType\":\"NOTE\",\"description\":\"left a note\"}",
                scene.worker());

        assertThat(jdbc.queryForObject("select status from maintenance_request where id=?",
                String.class, scene.request())).isEqualTo("ASSIGNED");
        assertThat(version(scene.request())).isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from request_history where request_id=?",
                Long.class, scene.request())).isEqualTo(before);
    }

    @Test
    void allFiveFrozenActionTypesAreAcceptedAndAnythingElseIsRejected() {
        Scene scene = assigned();

        for (String type : List.of("INSPECTION", "REPAIR", "REPLACEMENT", "TEST", "NOTE")) {
            assertThat(append(scene.request(),
                    "{\"actionType\":\"" + type + "\",\"description\":\"did " + type + "\"}",
                    scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> bad = append(scene.request(),
                "{\"actionType\":\"NOPE\",\"description\":\"x\"}", scene.worker());
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).contains("VALIDATION_FAILED");
    }

    @Test
    void aDescriptionIsRequiredAndBounded() {
        Scene scene = assigned();

        assertThat(append(scene.request(), "{\"actionType\":\"NOTE\"}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(append(scene.request(), "{\"actionType\":\"NOTE\",\"description\":\"\"}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String tooLong = "x".repeat(2001);
        assertThat(append(scene.request(),
                "{\"actionType\":\"NOTE\",\"description\":\"" + tooLong + "\"}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(append(scene.request(),
                "{\"actionType\":\"NOTE\",\"description\":\"" + "x".repeat(2000) + "\"}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownFieldsAreRejected() {
        Scene scene = assigned();

        assertThat(append(scene.request(),
                "{\"actionType\":\"NOTE\",\"description\":\"x\",\"workerId\":1}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(append(scene.request(),
                "{\"actionType\":\"NOTE\",\"description\":\"x\",\"createdAt\":\"2020-01-01T00:00:00Z\"}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyTheCurrentAssigneeAppendsAndAdministratorsCannot() {
        Scene scene = assigned();
        String body = "{\"actionType\":\"NOTE\",\"description\":\"x\"}";

        ResponseEntity<String> stranger = append(scene.request(), body,
                headers(user("WORKER"), "WORKER"));
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stranger.getBody()).contains("WORKER_NOT_ASSIGNED");

        ResponseEntity<String> admin = append(scene.request(), body, scene.admin());
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(admin.getBody()).contains("ACCESS_DENIED")
                .doesNotContain("WORKER_NOT_ASSIGNED");

        assertThat(append(scene.request(), body, scene.resident()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aFormerAssigneeCannotAppendButStillReads() {
        Scene scene = assigned();
        append(scene.request(), "{\"actionType\":\"REPAIR\",\"description\":\"mine\"}",
                scene.worker());
        long other = user("WORKER");
        http.exchange("/api/v1/maintenance-requests/" + scene.request() + "/assignments",
                HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + other + ",\"version\":"
                        + version(scene.request()) + "}", scene.admin()),
                String.class);

        assertThat(append(scene.request(), "{\"actionType\":\"NOTE\",\"description\":\"late\"}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> read = list(scene.request(), "", scene.worker());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).contains("mine");
    }

    @Test
    void aSuppliedVisitMustBelongToTheSameRequest() {
        Scene scene = assigned();
        long visit = scheduleVisit(scene);
        Scene other = assigned();
        long foreignVisit = scheduleVisit(other);

        assertThat(append(scene.request(),
                "{\"actionType\":\"INSPECTION\",\"description\":\"checked\",\"visitId\":" + visit
                        + "}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> foreign = append(scene.request(),
                "{\"actionType\":\"INSPECTION\",\"description\":\"x\",\"visitId\":" + foreignVisit
                        + "}",
                scene.worker());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(foreign.getBody()).contains("INVALID_REQUEST_STATE");

        ResponseEntity<String> missing = append(scene.request(),
                "{\"actionType\":\"INSPECTION\",\"description\":\"x\",\"visitId\":999999}",
                scene.worker());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("VISIT_NOT_FOUND");
    }

    @Test
    void aCancelledVisitMayStillBeDescribed() {
        Scene scene = assigned();
        long visit = scheduleVisit(scene);
        http.exchange("/api/v1/visits/" + visit + "/cancel", HttpMethod.POST,
                new HttpEntity<>("{\"note\":\"not needed\"}", scene.worker()), String.class);

        assertThat(append(scene.request(),
                "{\"actionType\":\"NOTE\",\"description\":\"went and it was fine\",\"visitId\":"
                        + visit + "}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void readingIsStaffOnlyAndAResidentIsDeniedOnTheirOwnRequest() {
        Scene scene = assigned();
        append(scene.request(), "{\"actionType\":\"REPAIR\",\"description\":\"internal detail\"}",
                scene.worker());

        assertThat(list(scene.request(), "", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(list(scene.request(), "", scene.admin()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(list(scene.request(), "", headers(user("SUPER_ADMIN"), "SUPER_ADMIN"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // The resident owns this request, so 403 discloses nothing they did not know.
        ResponseEntity<String> resident = list(scene.request(), "", scene.resident());
        assertThat(resident.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resident.getBody()).contains("ACCESS_DENIED")
                .doesNotContain("internal detail");
    }

    @Test
    void anOutOfScopeAdminIsDeniedAndAMissingRequestIsNotFound() {
        Scene scene = assigned();
        long otherAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                otherAdmin, dormitory());

        assertThat(list(scene.request(), "", headers(otherAdmin, "ADMIN")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> missing = list(999999L, "", scene.admin());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("REQUEST_NOT_FOUND");
    }

    @Test
    void theListReadsOldestFirstAndPaginates() {
        Scene scene = assigned();
        for (String type : List.of("INSPECTION", "REPAIR", "TEST")) {
            append(scene.request(),
                    "{\"actionType\":\"" + type + "\",\"description\":\"step " + type + "\"}",
                    scene.worker());
        }

        String body = list(scene.request(), "", scene.worker()).getBody();
        assertThat(body.indexOf("step INSPECTION")).isLessThan(body.indexOf("step REPAIR"));
        assertThat(body.indexOf("step REPAIR")).isLessThan(body.indexOf("step TEST"));
        assertThat(body).contains("\"totalElements\":3").contains("\"page\":0")
                .contains("\"size\":20");

        String firstPage = list(scene.request(), "?page=0&size=2", scene.worker()).getBody();
        assertThat(firstPage).contains("step INSPECTION").contains("step REPAIR")
                .doesNotContain("step TEST");
        assertThat(firstPage).contains("\"totalPages\":2");
        assertThat(list(scene.request(), "?page=1&size=2", scene.worker()).getBody())
                .contains("step TEST").doesNotContain("step INSPECTION");
    }

    @Test
    void paginationBoundsAreEnforced() {
        Scene scene = assigned();

        for (String query : List.of("?size=0", "?size=101", "?page=-1")) {
            ResponseEntity<String> response = list(scene.request(), query, scene.worker());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("VALIDATION_FAILED");
        }
    }

    @Test
    void aTerminalRequestTakesNoWorkLog() {
        Scene scene = assigned();
        post(scene.request(), "start", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
        post(scene.request(), "resolve", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
        post(scene.request(), "close", "{\"version\":" + version(scene.request()) + "}",
                scene.resident());
        assertThat(jdbc.queryForObject("select status from maintenance_request where id=?",
                String.class, scene.request())).isEqualTo("CLOSED");

        // Closing ended the assignment, so the authority check stops the worker first.
        ResponseEntity<String> response = append(scene.request(),
                "{\"actionType\":\"NOTE\",\"description\":\"late\"}", scene.worker());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from work_log where request_id=?",
                Long.class, scene.request())).isZero();
    }

    @Test
    void thereIsNoUpdateOrDeletePath() {
        Scene scene = assigned();
        append(scene.request(), "{\"actionType\":\"NOTE\",\"description\":\"x\"}", scene.worker());
        long id = jdbc.queryForObject("select id from work_log where request_id=?", Long.class,
                scene.request());

        for (HttpMethod method : List.of(HttpMethod.DELETE, HttpMethod.PUT)) {
            ResponseEntity<String> response = http.exchange("/api/v1/work-logs/" + id, method,
                    new HttpEntity<>("{}", scene.worker()), String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
        }
        assertThat(jdbc.queryForObject("select count(*) from work_log where id=?", Long.class, id))
                .isEqualTo(1);
    }

    private long scheduleVisit(Scene scene) {
        http.exchange("/api/v1/maintenance-requests/" + scene.request() + "/visits",
                HttpMethod.POST,
                new HttpEntity<>("{\"scheduledStart\":\"2026-11-08T10:00:00Z\","
                        + "\"scheduledEnd\":\"2026-11-08T11:00:00Z\"}", scene.worker()),
                String.class);
        return jdbc.queryForObject(
                "select id from maintenance_visit where request_id=? and status='SCHEDULED'",
                Long.class, scene.request());
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private ResponseEntity<String> append(long request, String body, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/work-logs",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> list(long request, String query, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/work-logs" + query,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(long request, String command, String body,
            HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/" + command,
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private Scene assigned() {
        long dormitory = dormitory();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        long request = request(resident, room);
        long admin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)", admin,
                dormitory);
        long worker = user("WORKER");
        Scene scene = new Scene(request, resident, admin, worker, headers(resident, "RESIDENT"),
                headers(admin, "ADMIN"), headers(worker, "WORKER"));
        http.exchange("/api/v1/maintenance-requests/" + request + "/assignments", HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + worker + ",\"version\":" + version(request)
                        + "}", scene.admin()), String.class);
        return scene;
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
        jdbc.update("insert into request_history(request_id,actor_id,event_type,new_status)"
                + " values (?,?,'REQUEST_CREATED','REPORTED')", id, reporter);
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
                "insert into building(dormitory_id,code,name) values (?,?,?) returning id",
                Long.class, dormitory, key(), "B");
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

    private record Scene(long request, long residentId, long adminId, long workerId,
            HttpHeaders resident, HttpHeaders admin, HttpHeaders worker) {
    }
}

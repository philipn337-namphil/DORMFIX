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
import java.util.ArrayList;
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

/** ADR-028: GET /maintenance-requests/{id}/history and the lifecycle reason it now carries. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestHistoryIntegrationTest {
    // Quote and backslash: concatenated JSON would break or be extended by this text.
    private static final String TRICKY_REASON = "waiting for \"part\" \\ ok\",\"x\":1";
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
    void everyReaderOfTheRequestReadsItsHistory() throws Exception {
        Scene scene = scene(true);

        for (HttpHeaders reader : List.of(scene.resident(), scene.worker(), scene.admin(),
                headers(user("SUPER_ADMIN"), "SUPER_ADMIN"))) {
            ResponseEntity<String> response = list(scene.request(), "", reader);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(events(response)).containsExactly("REQUEST_ASSIGNED");
        }
    }

    @Test
    void aRequestOutsideVisibilityAnswersLikeAMissingOne() {
        Scene scene = scene(true);
        long otherAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                otherAdmin, dormitory());

        for (HttpHeaders outsider : List.of(headers(user("WORKER"), "WORKER"),
                headers(user("RESIDENT"), "RESIDENT"), headers(otherAdmin, "ADMIN"))) {
            ResponseEntity<String> response = list(scene.request(), "", outsider);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).contains("REQUEST_NOT_FOUND");
        }
        ResponseEntity<String> missing = list(Long.MAX_VALUE, "", scene.admin());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("REQUEST_NOT_FOUND");
    }

    @Test
    void rowsReadOldestFirstWithTypedFieldsAndNoRawMetadata() throws Exception {
        Scene scene = scene(true);
        command(scene.request(), "start", null, scene.worker());
        command(scene.request(), "hold", TRICKY_REASON, scene.worker());
        command(scene.request(), "resume", null, scene.worker());

        ResponseEntity<String> response = list(scene.request(), "", scene.resident());

        assertThat(response.getBody()).doesNotContain("\"metadata\"");
        assertThat(events(response)).containsExactly("REQUEST_ASSIGNED", "REQUEST_STARTED",
                "REQUEST_HELD", "REQUEST_RESUMED");
        JsonNode rows = JSON.readTree(response.getBody()).get("content");
        assertThat(rows.get(0).get("workerId").asLong()).isEqualTo(scene.workerId());
        assertThat(rows.get(0).get("newStatus").asText()).isEqualTo("ASSIGNED");
        assertThat(rows.get(1).get("reason").isNull()).isTrue();
        assertThat(rows.get(2).get("reason").asText()).isEqualTo(TRICKY_REASON);
        assertThat(rows.get(2).get("previousStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(rows.get(2).get("newStatus").asText()).isEqualTo("ON_HOLD");
        assertThat(rows.get(2).get("actorId").asLong()).isEqualTo(scene.workerId());
        assertThat(rows.get(2).get("changedFields").isNull()).isTrue();
    }

    @Test
    void lifecycleReasonsAreStoredAsJsonAndSurviveTheNextTransition() {
        Scene rejected = scene(false);
        command(rejected.request(), "reject", TRICKY_REASON, rejected.admin());
        assertThat(jdbc.queryForObject("select metadata->>'reason' from request_history"
                + " where request_id=? and event_type='REQUEST_REJECTED'", String.class,
                rejected.request())).isEqualTo(TRICKY_REASON);
        assertThat(jdbc.queryForObject("select count(*) from request_history where request_id=?"
                + " and metadata->>'x' is not null", Long.class, rejected.request())).isZero();

        Scene original = scene(false);
        Scene duplicate = scene(false, original);
        http.exchange("/api/v1/maintenance-requests/" + duplicate.request() + "/mark-duplicate",
                HttpMethod.POST, new HttpEntity<>("{\"duplicateOfId\":" + original.request()
                        + ",\"statusReason\":\"same leak\",\"version\":"
                        + version(duplicate.request()) + "}", duplicate.admin()),
                String.class);
        assertThat(jdbc.queryForObject("select metadata->>'duplicateOfId' || '|' ||"
                + " (metadata->>'reason') from request_history where request_id=?"
                + " and event_type='REQUEST_MARKED_DUPLICATE'", String.class,
                duplicate.request())).isEqualTo(original.request() + "|same leak");

        // status_reason is overwritten by the next transition; the history row keeps the reason.
        Scene held = scene(true);
        command(held.request(), "start", null, held.worker());
        command(held.request(), "hold", "no parts", held.worker());
        command(held.request(), "resume", null, held.worker());
        assertThat(jdbc.queryForObject("select status_reason from maintenance_request where id=?",
                String.class, held.request())).isNull();
        assertThat(jdbc.queryForObject("select metadata->>'reason' from request_history"
                + " where request_id=? and event_type='REQUEST_HELD'", String.class,
                held.request())).isEqualTo("no parts");
        assertThat(jdbc.queryForObject("select metadata from request_history"
                + " where request_id=? and event_type='REQUEST_RESUMED'", String.class,
                held.request())).isNull();
    }

    @Test
    void aResidentNeverReceivesCommentDeletedRowsOrTheirCount() throws Exception {
        Scene scene = scene(true);
        long comment = addComment(scene.request(), "STAFF_ONLY", scene.worker());
        http.exchange("/api/v1/comments/" + comment, HttpMethod.DELETE,
                new HttpEntity<>(scene.admin()), String.class);

        ResponseEntity<String> staff = list(scene.request(), "?size=1", scene.worker());
        assertThat(JSON.readTree(staff.getBody()).get("totalElements").asLong()).isEqualTo(2);
        JsonNode deleted = JSON.readTree(list(scene.request(), "", scene.admin()).getBody())
                .get("content").get(1);
        assertThat(deleted.get("eventType").asText()).isEqualTo("COMMENT_DELETED");
        assertThat(deleted.get("commentId").asLong()).isEqualTo(comment);

        ResponseEntity<String> resident = list(scene.request(), "?size=1", scene.resident());
        assertThat(resident.getBody()).doesNotContain("COMMENT_DELETED");
        JsonNode page = JSON.readTree(resident.getBody());
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("totalPages").asInt()).isEqualTo(1);

        // The reporter also holds WORKER but was never assigned: still the resident view.
        HttpHeaders reporterWorker = headers(scene.residentId(), "RESIDENT", "WORKER");
        assertThat(list(scene.request(), "", reporterWorker).getBody())
                .doesNotContain("COMMENT_DELETED");
    }

    @Test
    void aFormerAssigneeKeepsTheStaffView() throws Exception {
        Scene scene = scene(true);
        long comment = addComment(scene.request(), "STAFF_ONLY", scene.worker());
        http.exchange("/api/v1/comments/" + comment, HttpMethod.DELETE,
                new HttpEntity<>(scene.admin()), String.class);
        long next = user("WORKER");
        http.exchange("/api/v1/maintenance-requests/" + scene.request() + "/assignments",
                HttpMethod.POST, new HttpEntity<>("{\"workerId\":" + next + ",\"version\":"
                        + version(scene.request()) + "}", scene.admin()), String.class);

        ResponseEntity<String> former = list(scene.request(), "", scene.worker());

        assertThat(former.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events(former)).containsExactly("REQUEST_ASSIGNED", "COMMENT_DELETED",
                "REQUEST_REASSIGNED");
        JsonNode reassigned = JSON.readTree(former.getBody()).get("content").get(2);
        assertThat(reassigned.get("workerId").asLong()).isEqualTo(next);
        assertThat(reassigned.get("previousWorkerId").asLong()).isEqualTo(scene.workerId());
    }

    @Test
    void paginationIsBoundedAndPagesFollowTheOrder() throws Exception {
        Scene scene = scene(true);
        command(scene.request(), "start", null, scene.worker());
        command(scene.request(), "resolve", null, scene.worker());

        assertThat(events(list(scene.request(), "?page=1&size=2", scene.resident())))
                .containsExactly("REQUEST_RESOLVED");
        for (String query : List.of("?size=0", "?size=101", "?page=-1")) {
            ResponseEntity<String> response = list(scene.request(), query, scene.resident());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("VALIDATION_FAILED");
        }
    }

    private List<String> events(ResponseEntity<String> response) throws Exception {
        List<String> events = new ArrayList<>();
        JSON.readTree(response.getBody()).get("content")
                .forEach(row -> events.add(row.get("eventType").asText()));
        return events;
    }

    private ResponseEntity<String> list(long request, String query, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/history" + query,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void command(long request, String command, String reason, HttpHeaders headers) {
        String reasonField = "";
        if (reason != null) {
            try {
                reasonField = "\"statusReason\":" + JSON.writeValueAsString(reason) + ",";
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }
        ResponseEntity<String> response = http.exchange(
                "/api/v1/maintenance-requests/" + request + "/" + command, HttpMethod.POST,
                new HttpEntity<>("{" + reasonField + "\"version\":" + version(request) + "}",
                        headers), String.class);
        assertThat(response.getStatusCode()).as(command + ": " + response.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    private long addComment(long request, String visibility, HttpHeaders headers) {
        ResponseEntity<String> response = http.exchange(
                "/api/v1/maintenance-requests/" + request + "/comments", HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"internal\",\"visibility\":\"" + visibility
                        + "\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jdbc.queryForObject("select max(id) from comment where request_id=?", Long.class,
                request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private Scene scene(boolean assign) {
        return scene(assign, null);
    }

    /** A request in REPORTED, assigned when asked; {@code sibling} shares its dormitory and admin. */
    private Scene scene(boolean assign, Scene sibling) {
        long dormitory = sibling == null ? dormitory() : sibling.dormitoryId();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        long category = jdbc.queryForObject(
                "insert into maintenance_category(code,name,default_priority,sort_order)"
                        + " values (?,?,'HIGH',1) returning id",
                Long.class, key(), "Leaks");
        long request = jdbc.queryForObject(
                "insert into maintenance_request(request_number,reporter_id,space_id,category_id,title,"
                        + "description,priority,status,entry_policy,contact_before_entry)"
                        + " values (?,?,?,?,'Leak','Ceiling leak','HIGH','REPORTED',"
                        + "'RESIDENT_PRESENT_REQUIRED',true) returning id",
                Long.class, "P-" + key(), resident, room, category);
        jdbc.update("update maintenance_request set request_number=? where id=?", "DF-" + request,
                request);
        long admin;
        if (sibling == null) {
            admin = user("ADMIN");
            jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                    admin, dormitory);
        } else {
            admin = sibling.adminId();
        }
        long worker = user("WORKER");
        Scene scene = new Scene(request, dormitory, resident, admin, worker,
                headers(resident, "RESIDENT"), headers(admin, "ADMIN"), headers(worker, "WORKER"));
        if (assign) {
            http.exchange("/api/v1/maintenance-requests/" + request + "/assignments",
                    HttpMethod.POST, new HttpEntity<>("{\"workerId\":" + worker + ",\"version\":"
                            + version(request) + "}", scene.admin()), String.class);
        }
        return scene;
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

    private record Scene(long request, long dormitoryId, long residentId, long adminId,
            long workerId, HttpHeaders resident, HttpHeaders admin, HttpHeaders worker) {
    }
}

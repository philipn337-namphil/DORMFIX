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
class CommentIntegrationTest {
    private static final String SECRET = "internal remark do not show";

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
    void theReporterWritesPublicAndTheRequestIsLeftUntouched() {
        Scene scene = assigned();
        long historyBefore = historyCount(scene.request());
        long version = version(scene.request());

        ResponseEntity<String> response = add(scene.request(), "is anyone coming?", "PUBLIC",
                scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("is anyone coming?").contains("PUBLIC");
        assertThat(jdbc.queryForObject("select author_id from comment where request_id=?",
                Long.class, scene.request())).isEqualTo(scene.residentId());
        assertThat(version(scene.request())).isEqualTo(version);
        assertThat(historyCount(scene.request())).isEqualTo(historyBefore);
    }

    @Test
    void aResidentCannotWriteStaffOnly() {
        Scene scene = assigned();

        ResponseEntity<String> response = add(scene.request(), "x", "STAFF_ONLY",
                scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
        assertThat(commentCount(scene.request())).isZero();
    }

    @Test
    void theAssigneeAndAScopedAdministratorWriteBothVisibilities() {
        Scene scene = assigned();

        for (HttpHeaders staff : List.of(scene.worker(), scene.admin(),
                headers(user("SUPER_ADMIN"), "SUPER_ADMIN"))) {
            assertThat(add(scene.request(), "public note", "PUBLIC", staff).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(add(scene.request(), "staff note", "STAFF_ONLY", staff).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        assertThat(commentCount(scene.request())).isEqualTo(6);
    }

    @Test
    void visibilityIsRequiredHasNoDefaultAndContentIsBounded() {
        Scene scene = assigned();

        assertThat(raw(scene.request(), "{\"content\":\"x\"}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(commentCount(scene.request())).isZero();
        ResponseEntity<String> bad = add(scene.request(), "x", "EVERYONE", scene.worker());
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).contains("VALIDATION_FAILED");
        assertThat(add(scene.request(), "x".repeat(2001), "PUBLIC", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(raw(scene.request(),
                "{\"content\":\"x\",\"visibility\":\"PUBLIC\",\"authorId\":1}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aRoleWithoutAStaffRelationshipToThisRequestGainsNothing() {
        Scene scene = assigned();

        // A worker unrelated to the request cannot even see it.
        ResponseEntity<String> stranger = add(scene.request(), "x", "STAFF_ONLY",
                headers(user("WORKER"), "WORKER"));
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stranger.getBody()).contains("REQUEST_NOT_FOUND");

        // The reporter also holds WORKER but is not assigned: still a resident here.
        add(scene.request(), SECRET, "STAFF_ONLY", scene.worker());
        HttpHeaders reporterWorker = headers(scene.residentId(), "RESIDENT", "WORKER");
        assertThat(add(scene.request(), "x", "STAFF_ONLY", reporterWorker).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(list(scene.request(), "", reporterWorker).getBody()).doesNotContain(SECRET);

        // An out-of-scope administrator cannot see the request either.
        long otherAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                otherAdmin, dormitory());
        assertThat(list(scene.request(), "", headers(otherAdmin, "ADMIN")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aResidentNeverReceivesStaffOnlyRows() {
        Scene scene = assigned();
        add(scene.request(), "hello resident", "PUBLIC", scene.worker());
        add(scene.request(), SECRET, "STAFF_ONLY", scene.worker());
        add(scene.request(), SECRET + " two", "STAFF_ONLY", scene.admin());

        String resident = list(scene.request(), "", scene.resident()).getBody();
        assertThat(resident).contains("hello resident").doesNotContain(SECRET)
                .doesNotContain("STAFF_ONLY");

        String staff = list(scene.request(), "", scene.worker()).getBody();
        assertThat(staff).contains("hello resident").contains(SECRET).contains("STAFF_ONLY");
    }

    @Test
    void theResidentTotalsCountPublicCommentsOnly() {
        Scene scene = assigned();
        add(scene.request(), "one", "PUBLIC", scene.worker());
        add(scene.request(), SECRET, "STAFF_ONLY", scene.worker());
        add(scene.request(), SECRET, "STAFF_ONLY", scene.worker());
        add(scene.request(), SECRET, "STAFF_ONLY", scene.admin());

        String resident = list(scene.request(), "?size=1", scene.resident()).getBody();
        assertThat(resident).contains("\"totalElements\":1").contains("\"totalPages\":1");

        String staff = list(scene.request(), "?size=1", scene.worker()).getBody();
        assertThat(staff).contains("\"totalElements\":4").contains("\"totalPages\":4");
    }

    @Test
    void theListReadsOldestFirstAndPaginationIsBounded() {
        Scene scene = assigned();
        add(scene.request(), "first", "PUBLIC", scene.resident());
        add(scene.request(), "second", "PUBLIC", scene.worker());
        add(scene.request(), "third", "PUBLIC", scene.resident());

        String body = list(scene.request(), "", scene.resident()).getBody();
        assertThat(body.indexOf("first")).isLessThan(body.indexOf("second"));
        assertThat(body.indexOf("second")).isLessThan(body.indexOf("third"));
        assertThat(list(scene.request(), "?page=1&size=2", scene.resident()).getBody())
                .contains("third").doesNotContain("first");

        for (String query : List.of("?size=0", "?size=101", "?page=-1")) {
            assertThat(list(scene.request(), query, scene.resident()).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void commentsAreAcceptedUntilTheRequestIsTerminalIncludingResolved() {
        Scene scene = assigned();
        post(scene.request(), "start", scene.worker());
        post(scene.request(), "resolve", scene.worker());

        assertThat(add(scene.request(), "is it really fixed?", "PUBLIC", scene.resident())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(add(scene.request(), "confirmed by phone", "STAFF_ONLY", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        post(scene.request(), "close", scene.resident());
        ResponseEntity<String> closed = add(scene.request(), "thanks", "PUBLIC",
                scene.resident());
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(closed.getBody()).contains("INVALID_REQUEST_STATE");
        assertThat(add(scene.request(), "late note", "STAFF_ONLY", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aFormerAssigneeStillReadsButCannotWriteOrDeleteTheirOwnComment() {
        Scene scene = assigned();
        add(scene.request(), SECRET, "STAFF_ONLY", scene.worker());
        long own = lastCommentId(scene.request());
        reassign(scene);

        assertThat(list(scene.request(), "", scene.worker()).getBody()).contains(SECRET);
        ResponseEntity<String> write = add(scene.request(), "x", "PUBLIC", scene.worker());
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(write.getBody()).contains("WORKER_NOT_ASSIGNED");
        assertThat(delete(own, scene.worker()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deletedAt(own)).isNull();
    }

    @Test
    void anAuthorDeletesTheirOwnCommentAndItLeavesEveryListingAndCount() {
        Scene scene = assigned();
        add(scene.request(), "typo", "PUBLIC", scene.resident());
        long id = lastCommentId(scene.request());
        add(scene.request(), "kept", "PUBLIC", scene.resident());

        ResponseEntity<String> response = delete(id, scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deletedAt(id)).isNotNull();
        assertThat(jdbc.queryForObject("select content from comment where id=?", String.class, id))
                .isEqualTo("typo");
        for (HttpHeaders reader : List.of(scene.resident(), scene.worker(), scene.admin())) {
            String body = list(scene.request(), "", reader).getBody();
            assertThat(body).contains("kept").doesNotContain("typo")
                    .contains("\"totalElements\":1");
        }
    }

    @Test
    void deletionRecordsWhoDidItWithoutCopyingTheContent() {
        Scene scene = assigned();
        add(scene.request(), "posted a phone number 010", "PUBLIC", scene.resident());
        long id = lastCommentId(scene.request());

        assertThat(delete(id, scene.admin()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject(
                "select actor_id from request_history where request_id=?"
                        + " and event_type='COMMENT_DELETED'",
                Long.class, scene.request())).isEqualTo(scene.adminId());
        String metadata = jdbc.queryForObject(
                "select metadata::text from request_history where request_id=?"
                        + " and event_type='COMMENT_DELETED'",
                String.class, scene.request());
        assertThat(metadata).contains("commentId").contains(Long.toString(id))
                .doesNotContain("phone").doesNotContain("010");
    }

    @Test
    void deletingACommentTheCallerCannotReadLooksExactlyLikeAMissingComment() {
        Scene scene = assigned();
        add(scene.request(), SECRET, "STAFF_ONLY", scene.worker());
        long staffOnly = lastCommentId(scene.request());

        ResponseEntity<String> hidden = delete(staffOnly, scene.resident());
        ResponseEntity<String> missing = delete(999999L, scene.resident());

        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getStatusCode()).isEqualTo(missing.getStatusCode());
        assertThat(hidden.getBody()).contains("COMMENT_NOT_FOUND").doesNotContain("ACCESS_DENIED")
                .doesNotContain(SECRET);
        assertThat(missing.getBody()).contains("COMMENT_NOT_FOUND");
        assertThat(deletedAt(staffOnly)).isNull();

        // A caller from another dormitory gets the same answer.
        assertThat(delete(staffOnly, headers(user("RESIDENT"), "RESIDENT")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aReadableCommentByAnotherAuthorIsForbiddenAndADeletedOneIsNotFound() {
        Scene scene = assigned();
        add(scene.request(), "from the worker", "PUBLIC", scene.worker());
        long workers = lastCommentId(scene.request());

        ResponseEntity<String> notMine = delete(workers, scene.resident());
        assertThat(notMine.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(notMine.getBody()).contains("ACCESS_DENIED");

        assertThat(delete(workers, scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> again = delete(workers, scene.worker());
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(again.getBody()).contains("COMMENT_NOT_FOUND");
    }

    @Test
    void deletionIsNotBlockedByAClosedRequest() {
        Scene scene = assigned();
        add(scene.request(), "my number is 010", "PUBLIC", scene.resident());
        long id = lastCommentId(scene.request());
        post(scene.request(), "start", scene.worker());
        post(scene.request(), "resolve", scene.worker());
        post(scene.request(), "close", scene.resident());

        assertThat(delete(id, scene.resident()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void thereIsNoCommentUpdatePath() {
        Scene scene = assigned();
        add(scene.request(), "original", "PUBLIC", scene.resident());
        long id = lastCommentId(scene.request());

        ResponseEntity<String> response = http.exchange("/api/v1/comments/" + id, HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"edited\"}", scene.resident()), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(jdbc.queryForObject("select content from comment where id=?", String.class, id))
                .isEqualTo("original");
    }

    private Instant deletedAt(long comment) {
        java.sql.Timestamp value = jdbc.queryForObject(
                "select deleted_at from comment where id=?", java.sql.Timestamp.class, comment);
        return value == null ? null : value.toInstant();
    }

    private long lastCommentId(long request) {
        return jdbc.queryForObject("select max(id) from comment where request_id=?", Long.class,
                request);
    }

    private long commentCount(long request) {
        return jdbc.queryForObject("select count(*) from comment where request_id=?", Long.class,
                request);
    }

    private long historyCount(long request) {
        return jdbc.queryForObject("select count(*) from request_history where request_id=?",
                Long.class, request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private ResponseEntity<String> add(long request, String content, String visibility,
            HttpHeaders headers) {
        return raw(request, "{\"content\":\"" + content + "\",\"visibility\":\"" + visibility
                + "\"}", headers);
    }

    private ResponseEntity<String> raw(long request, String body, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/comments",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> list(long request, String query, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/comments" + query,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> delete(long comment, HttpHeaders headers) {
        return http.exchange("/api/v1/comments/" + comment, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
    }

    private void post(long request, String command, HttpHeaders headers) {
        http.exchange("/api/v1/maintenance-requests/" + request + "/" + command, HttpMethod.POST,
                new HttpEntity<>("{\"version\":" + version(request) + "}", headers), String.class);
    }

    private void reassign(Scene scene) {
        http.exchange("/api/v1/maintenance-requests/" + scene.request() + "/assignments",
                HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + user("WORKER") + ",\"version\":"
                        + version(scene.request()) + "}", scene.admin()),
                String.class);
    }

    private Scene assigned() {
        long dormitory = dormitory();
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
        long admin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)", admin,
                dormitory);
        long worker = user("WORKER");
        Scene scene = new Scene(request, resident, admin, headers(resident, "RESIDENT"),
                headers(admin, "ADMIN"), headers(worker, "WORKER"));
        http.exchange("/api/v1/maintenance-requests/" + request + "/assignments", HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + worker + ",\"version\":" + version(request)
                        + "}", scene.admin()), String.class);
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

    private record Scene(long request, long residentId, long adminId, HttpHeaders resident,
            HttpHeaders admin, HttpHeaders worker) {
    }
}

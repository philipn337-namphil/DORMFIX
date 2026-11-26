package com.dormfix.notification.api;

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
import java.time.LocalDate;
import java.time.ZoneId;
import com.dormfix.notification.application.NotificationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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

/** ADR-029: notifications written after commit, and the caller's own notification paths. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationIntegrationTest {
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
    @MockitoSpyBean
    NotificationRepository notificationRepository;

    @Test
    void aNewRequestReachesTheScopedAdministratorsOnly() {
        long dormitory = dormitory();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        jdbc.update("insert into residence(resident_id,room_space_id,start_date) values (?,?,?)",
                resident, room, java.sql.Date.valueOf(
                        LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)));
        long scoped = admin(dormitory);
        long secondScoped = admin(dormitory);
        long elsewhere = admin(dormitory());
        long superAdmin = user("SUPER_ADMIN");

        ResponseEntity<String> created = http.exchange("/api/v1/maintenance-requests",
                HttpMethod.POST, new HttpEntity<>("{\"spaceId\":" + room + ",\"categoryId\":"
                        + category() + ",\"title\":\"Secret title\",\"description\":"
                        + "\"Secret description\",\"entryPolicy\":\"RESIDENT_PRESENT_REQUIRED\","
                        + "\"contactBeforeEntry\":true}", headers(resident, "RESIDENT")),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(types(scoped)).containsExactly("REQUEST_SUBMITTED");
        assertThat(types(secondScoped)).containsExactly("REQUEST_SUBMITTED");
        assertThat(types(elsewhere)).isEmpty();
        assertThat(types(superAdmin)).isEmpty();
        assertThat(types(resident)).isEmpty();
        String text = jdbc.queryForObject("select title || ' ' || message from notification"
                + " where recipient_id=?", String.class, scoped);
        assertThat(text).doesNotContain("Secret").contains("DF-");
    }

    @Test
    void assignmentAndReassignmentTellEveryoneButTheActor() {
        Scene scene = scene(true);
        assertThat(types(scene.workerId())).containsExactly("ASSIGNED_TO_YOU");
        assertThat(types(scene.residentId())).containsExactly("REQUEST_ASSIGNED");
        assertThat(types(scene.adminId())).isEmpty();

        long next = user("WORKER");
        assign(scene, next);

        assertThat(types(next)).containsExactly("ASSIGNED_TO_YOU");
        assertThat(types(scene.workerId())).containsExactly("ASSIGNED_TO_YOU",
                "UNASSIGNED_FROM_YOU");
        assertThat(types(scene.residentId())).containsExactly("REQUEST_ASSIGNED",
                "REQUEST_ASSIGNED");
    }

    @Test
    void lifecycleAndVisitEventsFollowTheTable() {
        Scene scene = scene(true);
        command(scene.request(), "start", scene.worker());
        http.exchange("/api/v1/maintenance-requests/" + scene.request() + "/visits",
                HttpMethod.POST, new HttpEntity<>("{\"scheduledStart\":\"2030-01-01T01:00:00Z\","
                        + "\"scheduledEnd\":\"2030-01-01T02:00:00Z\"}", scene.admin()),
                String.class);
        command(scene.request(), "resolve", scene.worker());
        command(scene.request(), "close", scene.resident());

        assertThat(types(scene.residentId())).containsExactly("REQUEST_ASSIGNED",
                "REQUEST_STARTED", "VISIT_SCHEDULED", "REQUEST_RESOLVED");
        assertThat(types(scene.workerId())).containsExactly("ASSIGNED_TO_YOU", "VISIT_SCHEDULED",
                "REQUEST_CLOSED");
        assertThat(types(scene.adminId())).isEmpty();
    }

    @Test
    void aStaffOnlyCommentNeverReachesTheReporter() {
        Scene scene = scene(true);
        comment(scene.request(), "STAFF_ONLY", scene.admin());
        comment(scene.request(), "PUBLIC", scene.resident());

        assertThat(types(scene.residentId())).containsExactly("REQUEST_ASSIGNED");
        assertThat(types(scene.workerId())).containsExactly("ASSIGNED_TO_YOU", "COMMENT_ADDED",
                "COMMENT_ADDED");

        comment(scene.request(), "PUBLIC", scene.worker());
        assertThat(types(scene.residentId())).containsExactly("REQUEST_ASSIGNED", "COMMENT_ADDED");
    }

    @Test
    void aRejectedCommandProducesNoNotification() {
        Scene scene = scene(true);
        long before = count(scene.residentId());

        ResponseEntity<String> stale = http.exchange("/api/v1/maintenance-requests/"
                + scene.request() + "/start", HttpMethod.POST,
                new HttpEntity<>("{\"version\":" + (version(scene.request()) + 5) + "}",
                        scene.worker()), String.class);

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count(scene.residentId())).isEqualTo(before);
    }

    @Test
    void aFailingNotificationWriteDoesNotBreakTheCommittedCommand() {
        Scene scene = scene(true);
        doThrow(new IllegalStateException("notification store down"))
                .when(notificationRepository).saveAll(anyList());
        try {
            ResponseEntity<String> started = http.exchange("/api/v1/maintenance-requests/"
                    + scene.request() + "/start", HttpMethod.POST,
                    new HttpEntity<>("{\"version\":" + version(scene.request()) + "}",
                            scene.worker()), String.class);

            assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(jdbc.queryForObject("select status from maintenance_request where id=?",
                    String.class, scene.request())).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("select count(*) from request_history where"
                    + " request_id=? and event_type='REQUEST_STARTED'", Long.class,
                    scene.request())).isEqualTo(1);
            assertThat(types(scene.residentId())).containsExactly("REQUEST_ASSIGNED");
        } finally {
            reset(notificationRepository);
        }
    }

    @Test
    void theCallerReadsAndMarksOnlyTheirOwnNotifications() throws Exception {
        Scene scene = scene(true);
        command(scene.request(), "start", scene.worker());
        command(scene.request(), "hold", scene.worker(), "no parts");
        HttpHeaders resident = scene.resident();

        JsonNode page = JSON.readTree(get("/api/v1/notifications", resident).getBody());
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        List<String> listed = new ArrayList<>();
        page.get("content").forEach(row -> listed.add(row.get("type").asText()));
        assertThat(listed).containsExactly("REQUEST_HELD", "REQUEST_STARTED", "REQUEST_ASSIGNED");
        assertThat(page.toString()).doesNotContain("no parts").doesNotContain("recipientId");
        assertThat(count(get("/api/v1/notifications/unread-count", resident))).isEqualTo(3);

        long newest = page.get("content").get(0).get("id").asLong();
        ResponseEntity<String> read = patch("/api/v1/notifications/" + newest + "/read", resident);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        String readAt = JSON.readTree(read.getBody()).get("readAt").asText();
        assertThat(JSON.readTree(read.getBody()).get("read").asBoolean()).isTrue();
        ResponseEntity<String> again = patch("/api/v1/notifications/" + newest + "/read",
                resident);
        assertThat(JSON.readTree(again.getBody()).get("readAt").asText()).isEqualTo(readAt);
        assertThat(count(get("/api/v1/notifications/unread-count", resident))).isEqualTo(2);
        assertThat(JSON.readTree(get("/api/v1/notifications?unread=true", resident).getBody())
                .get("totalElements").asLong()).isEqualTo(2);

        // Another user's notification answers exactly like a missing one.
        for (HttpHeaders other : List.of(scene.worker(), scene.admin())) {
            ResponseEntity<String> foreign = patch("/api/v1/notifications/" + newest + "/read",
                    other);
            assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(foreign.getBody()).contains("NOTIFICATION_NOT_FOUND");
        }
        ResponseEntity<String> missing = patch("/api/v1/notifications/" + Long.MAX_VALUE
                + "/read", resident);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("NOTIFICATION_NOT_FOUND");

        long workerUnread = count(get("/api/v1/notifications/unread-count", scene.worker()));
        ResponseEntity<String> all = http.exchange("/api/v1/notifications/read-all",
                HttpMethod.POST, new HttpEntity<>(resident), String.class);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count(get("/api/v1/notifications/unread-count", resident))).isZero();
        assertThat(count(get("/api/v1/notifications/unread-count", scene.worker())))
                .isEqualTo(workerUnread);
    }

    @Test
    void paginationIsBoundedAndAnonymousCallersAreRejected() {
        Scene scene = scene(true);
        for (String query : List.of("?size=0", "?size=101", "?page=-1", "?page=x")) {
            ResponseEntity<String> response = get("/api/v1/notifications" + query,
                    scene.resident());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("VALIDATION_FAILED");
        }
        assertThat(http.getForEntity("/api/v1/notifications", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private List<String> types(long recipient) {
        return jdbc.queryForList("select type from notification where recipient_id=?"
                + " order by created_at, id", String.class, recipient);
    }

    private long count(long recipient) {
        return jdbc.queryForObject("select count(*) from notification where recipient_id=?",
                Long.class, recipient);
    }

    private long count(ResponseEntity<String> unreadCount) throws Exception {
        return JSON.readTree(unreadCount.getBody()).get("count").asLong();
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> patch(String path, HttpHeaders headers) {
        return http.exchange(path, HttpMethod.PATCH, new HttpEntity<>(headers), String.class);
    }

    private void command(long request, String command, HttpHeaders headers) {
        command(request, command, headers, null);
    }

    private void command(long request, String command, HttpHeaders headers, String reason) {
        String reasonField = reason == null ? "" : "\"statusReason\":\"" + reason + "\",";
        ResponseEntity<String> response = http.exchange(
                "/api/v1/maintenance-requests/" + request + "/" + command, HttpMethod.POST,
                new HttpEntity<>("{" + reasonField + "\"version\":" + version(request) + "}",
                        headers), String.class);
        assertThat(response.getStatusCode()).as(command + ": " + response.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    private void comment(long request, String visibility, HttpHeaders headers) {
        ResponseEntity<String> response = http.exchange(
                "/api/v1/maintenance-requests/" + request + "/comments", HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"private words\",\"visibility\":\""
                        + visibility + "\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void assign(Scene scene, long worker) {
        ResponseEntity<String> response = http.exchange("/api/v1/maintenance-requests/"
                + scene.request() + "/assignments", HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + worker + ",\"version\":"
                        + version(scene.request()) + "}", scene.admin()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private long admin(long dormitory) {
        long admin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)", admin,
                dormitory);
        return admin;
    }

    private long category() {
        return jdbc.queryForObject(
                "insert into maintenance_category(code,name,default_priority,sort_order)"
                        + " values (?,?,'HIGH',1) returning id",
                Long.class, key(), "Leaks");
    }

    /** A REPORTED request inserted directly, then assigned through the API when asked. */
    private Scene scene(boolean assign) {
        long dormitory = dormitory();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        long request = jdbc.queryForObject(
                "insert into maintenance_request(request_number,reporter_id,space_id,category_id,title,"
                        + "description,priority,status,entry_policy,contact_before_entry)"
                        + " values (?,?,?,?,'Leak','Ceiling leak','HIGH','REPORTED',"
                        + "'RESIDENT_PRESENT_REQUIRED',true) returning id",
                Long.class, "P-" + key(), resident, room, category());
        jdbc.update("update maintenance_request set request_number=? where id=?", "DF-" + request,
                request);
        long admin = admin(dormitory);
        long worker = user("WORKER");
        Scene scene = new Scene(request, resident, admin, worker, headers(resident, "RESIDENT"),
                headers(admin, "ADMIN"), headers(worker, "WORKER"));
        if (assign) {
            assign(scene, worker);
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

    private record Scene(long request, long residentId, long adminId, long workerId,
            HttpHeaders resident, HttpHeaders admin, HttpHeaders worker) {
    }
}

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
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
class MaintenanceVisitIntegrationTest {
    private static final String START = "2026-11-07T10:00:00Z";
    private static final String END = "2026-11-07T11:00:00Z";

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
    void theAssigneeSchedulesAVisitAndTheServerOwnsTheWorkerAndTheConsentSnapshot() {
        Scene scene = assigned();

        ResponseEntity<String> response = schedule(scene.request(), START, END, "bring a ladder",
                scene.worker());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SCHEDULED")
                .contains("RESIDENT_PRESENT_REQUIRED");
        assertThat(jdbc.queryForObject(
                "select worker_id from maintenance_visit where request_id=?", Long.class,
                scene.request())).isEqualTo(scene.workerId());
        assertThat(jdbc.queryForObject(
                "select created_by_id from maintenance_visit where request_id=?", Long.class,
                scene.request())).isEqualTo(scene.workerId());
        assertThat(jdbc.queryForObject(
                "select entry_policy_snapshot from maintenance_visit where request_id=?",
                String.class, scene.request())).isEqualTo("RESIDENT_PRESENT_REQUIRED");
        assertThat(jdbc.queryForObject(
                "select count(*) from request_history where request_id=?"
                        + " and event_type='VISIT_SCHEDULED'",
                Integer.class, scene.request())).isEqualTo(1);
    }

    @Test
    void anAdminSchedulesForTheAssigneeAndIsRecordedOnlyAsTheCreator() {
        Scene scene = assigned();

        assertThat(schedule(scene.request(), START, END, null, scene.admin()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("select worker_id from maintenance_visit where request_id=?",
                Long.class, scene.request())).isEqualTo(scene.workerId());
        assertThat(jdbc.queryForObject(
                "select created_by_id from maintenance_visit where request_id=?", Long.class,
                scene.request())).isEqualTo(scene.adminId());
    }

    @Test
    void theConsentSnapshotSurvivesALaterChangeToTheRequest() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());

        jdbc.update("update maintenance_request set entry_policy='ABSENT_ENTRY_ALLOWED',"
                + " contact_before_entry=false where id=?", scene.request());

        assertThat(jdbc.queryForObject(
                "select entry_policy_snapshot from maintenance_visit where request_id=?",
                String.class, scene.request())).isEqualTo("RESIDENT_PRESENT_REQUIRED");
        assertThat(jdbc.queryForObject(
                "select contact_before_entry from maintenance_visit where request_id=?",
                Boolean.class, scene.request())).isTrue();
    }

    @Test
    void onlyOneOpenVisitPerRequestAndTheDatabaseAlsoRefusesASecond() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());

        ResponseEntity<String> second = schedule(scene.request(), START, END, null, scene.worker());
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("INVALID_REQUEST_STATE");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "insert into maintenance_visit(request_id,worker_id,created_by_id,scheduled_start,"
                        + "scheduled_end,status,entry_policy_snapshot,contact_before_entry)"
                        + " values (?,?,?,?,?,'SCHEDULED','ABSENT_ENTRY_ALLOWED',false)",
                scene.request(), scene.workerId(), scene.workerId(),
                java.sql.Timestamp.from(Instant.parse(START)),
                java.sql.Timestamp.from(Instant.parse(END))))).isNotNull();
    }

    @Test
    void aFollowUpVisitIsAllowedOnceTheCurrentOneIsClosed() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        long visit = openVisit(scene.request());
        outcome(visit, "cancel", "{\"note\":\"resident rescheduled\"}", scene.worker());

        assertThat(schedule(scene.request(), START, END, null, scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                "select count(*) from maintenance_visit where request_id=?", Integer.class,
                scene.request())).isEqualTo(2);
    }

    @Test
    void schedulingNeedsAnAssignmentAndAnAcceptingRequestState() {
        Scene reported = reported();
        ResponseEntity<String> unassigned =
                schedule(reported.request(), START, END, null, reported.admin());
        assertThat(unassigned.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(unassigned.getBody()).contains("INVALID_REQUEST_STATE");

        Scene resolved = resolved();
        assertThat(schedule(resolved.request(), START, END, null, resolved.admin())
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anInvertedWindowIsInvalidVisitTime() {
        Scene scene = assigned();

        ResponseEntity<String> response = schedule(scene.request(), END, START, null,
                scene.worker());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_VISIT_TIME");
    }

    @Test
    void aPastWindowIsAcceptedSoAVisitCanBeRecordedAfterTheFact() {
        Scene scene = assigned();

        assertThat(schedule(scene.request(), "2020-01-01T09:00:00Z", "2020-01-01T10:00:00Z", null,
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aStrangerWorkerIsWorkerNotAssignedAndAResidentIsAccessDenied() {
        Scene scene = assigned();

        ResponseEntity<String> stranger = schedule(scene.request(), START, END, null,
                headers(user("WORKER"), "WORKER"));
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stranger.getBody()).contains("WORKER_NOT_ASSIGNED");

        ResponseEntity<String> resident = schedule(scene.request(), START, END, null,
                scene.resident());
        assertThat(resident.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resident.getBody()).contains("ACCESS_DENIED")
                .doesNotContain("WORKER_NOT_ASSIGNED");
    }

    @Test
    void patchMovesTheWindowAndAuditsOnlyWhenItActuallyMoved() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        long visit = openVisit(scene.request());

        assertThat(patch(visit, "{\"note\":\"second attempt\"}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rescheduleEvents(scene.request())).isZero();

        assertThat(patch(visit, "{\"scheduledStart\":\"2026-11-08T10:00:00Z\","
                + "\"scheduledEnd\":\"2026-11-08T11:00:00Z\"}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rescheduleEvents(scene.request())).isEqualTo(1);
    }

    @Test
    void patchRejectsAHalfWindowImmutableFieldsAndAClosedVisit() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        long visit = openVisit(scene.request());

        assertThat(patch(visit, "{\"scheduledStart\":\"2026-11-08T10:00:00Z\"}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patch(visit, "{\"status\":\"COMPLETED\"}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patch(visit, "{\"workerId\":1}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patch(visit, "{\"entryPolicySnapshot\":\"ABSENT_ENTRY_ALLOWED\"}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        outcome(visit, "cancel", "{\"note\":\"done\"}", scene.worker());
        assertThat(patch(visit, "{\"note\":\"late\"}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void completeStoresTheSuppliedActualWindowAndDoesNotResolveTheRequest() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        long visit = openVisit(scene.request());

        ResponseEntity<String> response = outcome(visit, "complete",
                "{\"actualStartedAt\":\"2026-11-07T10:05:00Z\","
                        + "\"actualEndedAt\":\"2026-11-07T10:40:00Z\",\"note\":\"fixed\"}",
                scene.worker());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visitStatus(visit)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "select actual_started_at is not null and actual_ended_at is not null"
                        + " from maintenance_visit where id=?",
                Boolean.class, visit)).isTrue();
        // Completing a visit is not resolving the request.
        assertThat(jdbc.queryForObject("select status from maintenance_request where id=?",
                String.class, scene.request())).isEqualTo("ASSIGNED");
    }

    @Test
    void completeRejectsAMissingOrInvertedActualWindow() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        long visit = openVisit(scene.request());

        assertThat(outcome(visit, "complete", "{\"actualStartedAt\":\"2026-11-07T10:05:00Z\"}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> inverted = outcome(visit, "complete",
                "{\"actualStartedAt\":\"2026-11-07T10:40:00Z\","
                        + "\"actualEndedAt\":\"2026-11-07T10:05:00Z\"}",
                scene.worker());
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inverted.getBody()).contains("INVALID_VISIT_TIME");
        assertThat(visitStatus(visit)).isEqualTo("SCHEDULED");
    }

    @Test
    void noAccessAndCancelRequireANoteAndRecordNoDuration() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        long visit = openVisit(scene.request());

        assertThat(outcome(visit, "no-access", "{}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(outcome(visit, "no-access", "{\"note\":\"nobody home\"}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visitStatus(visit)).isEqualTo("NO_ACCESS");
        assertThat(jdbc.queryForObject(
                "select actual_started_at is null and actual_ended_at is null"
                        + " from maintenance_visit where id=?",
                Boolean.class, visit)).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from request_history where request_id=?"
                        + " and event_type='VISIT_NO_ACCESS'",
                Integer.class, scene.request())).isEqualTo(1);
    }

    @Test
    void aTerminalRequestBlocksCompleteAndNoAccessButNeverCancel() {
        Scene scene = resolvedWithOpenVisit();
        long visit = openVisit(scene.request());
        // Close the request, which ends the assignment and leaves this visit open.
        post(scene.request(), "close", "{\"version\":" + version(scene.request()) + "}",
                scene.resident());
        assertThat(jdbc.queryForObject("select status from maintenance_request where id=?",
                String.class, scene.request())).isEqualTo("CLOSED");

        assertThat(outcome(visit, "complete",
                "{\"actualStartedAt\":\"2026-11-07T10:05:00Z\","
                        + "\"actualEndedAt\":\"2026-11-07T10:40:00Z\"}",
                scene.admin()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(outcome(visit, "no-access", "{\"note\":\"nobody home\"}", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(outcome(visit, "cancel", "{\"note\":\"request was closed\"}", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visitStatus(visit)).isEqualTo("CANCELED");
    }

    @Test
    void aResidentReadsTheScheduleWithoutTheStaffNote() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, "staff only remark", scene.worker());

        ResponseEntity<String> resident = list(scene.request(), scene.resident());
        assertThat(resident.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resident.getBody()).contains("SCHEDULED")
                .contains("RESIDENT_PRESENT_REQUIRED")
                .doesNotContain("staff only remark").doesNotContain("\"note\"");

        assertThat(list(scene.request(), scene.worker()).getBody()).contains("staff only remark");
        assertThat(list(scene.request(), scene.admin()).getBody()).contains("staff only remark");
    }

    @Test
    void anInvisibleRequestListsAsNotFoundRatherThanForbidden() {
        Scene scene = assigned();
        Scene elsewhere = assigned();

        ResponseEntity<String> response = list(elsewhere.request(), scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("REQUEST_NOT_FOUND").doesNotContain("ACCESS_DENIED");
    }

    @Test
    void theListIsOrderedNewestScheduledFirst() {
        Scene scene = assigned();
        schedule(scene.request(), "2026-11-05T10:00:00Z", "2026-11-05T11:00:00Z", null,
                scene.worker());
        long first = openVisit(scene.request());
        outcome(first, "cancel", "{\"note\":\"x\"}", scene.worker());
        schedule(scene.request(), "2026-11-09T10:00:00Z", "2026-11-09T11:00:00Z", null,
                scene.worker());
        long second = openVisit(scene.request());

        String body = list(scene.request(), scene.worker()).getBody();

        assertThat(body.indexOf("\"id\":" + second)).isLessThan(body.indexOf("\"id\":" + first));
    }

    @Test
    void aMissingVisitIsVisitNotFound() {
        Scene scene = assigned();

        ResponseEntity<String> response = outcome(999999L, "cancel", "{\"note\":\"x\"}",
                scene.admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("VISIT_NOT_FOUND");
    }

    @Test
    void theVisitAndItsHistoryRollBackTogether() {
        Scene scene = assigned();
        doThrow(new IllegalStateException("history write failed")).when(historyRepository)
                .save(any(RequestHistory.class));

        ResponseEntity<String> response = schedule(scene.request(), START, END, null,
                scene.worker());

        assertThat(response.getStatusCode().isError()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from maintenance_visit where request_id=?",
                Integer.class, scene.request())).isZero();
    }

    private int rescheduleEvents(long request) {
        return jdbc.queryForObject("select count(*) from request_history where request_id=?"
                + " and event_type='VISIT_RESCHEDULED'", Integer.class, request);
    }

    private String visitStatus(long visit) {
        return jdbc.queryForObject("select status from maintenance_visit where id=?", String.class,
                visit);
    }

    private long openVisit(long request) {
        return jdbc.queryForObject(
                "select id from maintenance_visit where request_id=? and status='SCHEDULED'",
                Long.class, request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private ResponseEntity<String> schedule(long request, String start, String end, String note,
            HttpHeaders headers) {
        String body = "{\"scheduledStart\":\"" + start + "\",\"scheduledEnd\":\"" + end + "\""
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
        return http.exchange("/api/v1/maintenance-requests/" + request + "/visits",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> list(long request, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/visits", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> patch(long visit, String body, HttpHeaders headers) {
        return patchTemplate().exchange("/api/v1/visits/" + visit, HttpMethod.PATCH,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> outcome(long visit, String command, String body,
            HttpHeaders headers) {
        return http.exchange("/api/v1/visits/" + visit + "/" + command, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> post(long request, String command, String body,
            HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/" + command,
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    /**
     * The default simple request factory cannot send PATCH. The JDK HttpClient factory can and
     * needs no extra dependency.
     */
    private TestRestTemplate patchTemplate() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        return http;
    }

    private Scene reported() {
        long dormitory = dormitory();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        long request = request(resident, room);
        long admin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)", admin,
                dormitory);
        long worker = user("WORKER");
        return new Scene(request, resident, admin, worker, headers(resident, "RESIDENT"),
                headers(admin, "ADMIN"), headers(worker, "WORKER"));
    }

    private Scene assigned() {
        Scene scene = reported();
        http.exchange("/api/v1/maintenance-requests/" + scene.request() + "/assignments",
                HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + scene.workerId() + ",\"version\":"
                        + version(scene.request()) + "}", scene.admin()),
                String.class);
        return scene;
    }

    private Scene resolved() {
        Scene scene = assigned();
        post(scene.request(), "start", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
        post(scene.request(), "resolve", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
        return scene;
    }

    private Scene resolvedWithOpenVisit() {
        Scene scene = assigned();
        schedule(scene.request(), START, END, null, scene.worker());
        post(scene.request(), "start", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
        post(scene.request(), "resolve", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
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

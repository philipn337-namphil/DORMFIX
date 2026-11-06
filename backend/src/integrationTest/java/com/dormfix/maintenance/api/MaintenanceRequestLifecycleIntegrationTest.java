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
class MaintenanceRequestLifecycleIntegrationTest {
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
    void theWorkerWalksTheRequestFromAssignedToResolvedAndTheAuditRecordsEachStep() {
        Scene scene = assigned();

        assertThat(post(scene.request(), "start", "{\"version\":" + version(scene.request()) + "}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(scene.request())).isEqualTo("IN_PROGRESS");

        assertThat(post(scene.request(), "hold",
                "{\"statusReason\":\"waiting for a part\",\"version\":" + version(scene.request())
                        + "}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(scene.request())).isEqualTo("ON_HOLD");
        assertThat(reason(scene.request())).isEqualTo("waiting for a part");

        assertThat(post(scene.request(), "resume", "{\"version\":" + version(scene.request()) + "}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(scene.request())).isEqualTo("IN_PROGRESS");
        assertThat(reason(scene.request())).isNull();

        assertThat(post(scene.request(), "resolve",
                "{\"statusReason\":\"fixed\",\"version\":" + version(scene.request()) + "}",
                scene.worker()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(scene.request())).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject(
                "select resolved_at is not null from maintenance_request where id=?", Boolean.class,
                scene.request())).isTrue();

        assertThat(events(scene.request())).containsExactly("REQUEST_CREATED", "REQUEST_ASSIGNED",
                "REQUEST_STARTED", "REQUEST_HELD", "REQUEST_RESUMED", "REQUEST_RESOLVED");
    }

    @Test
    void onlyTheCurrentAssigneeMayActAndAFormerAssigneeIsWorkerNotAssigned() {
        Scene scene = assigned();
        long otherWorker = user("WORKER");

        ResponseEntity<String> stranger = post(scene.request(), "start",
                "{\"version\":" + version(scene.request()) + "}", headers(otherWorker, "WORKER"));
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stranger.getBody()).contains("WORKER_NOT_ASSIGNED");

        // Reassign away, then the original worker is a former assignee.
        assign(scene.request(), otherWorker, version(scene.request()), scene.admin());
        ResponseEntity<String> former = post(scene.request(), "start",
                "{\"version\":" + version(scene.request()) + "}", scene.worker());
        assertThat(former.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(former.getBody()).contains("WORKER_NOT_ASSIGNED");
    }

    @Test
    void aRoleThatCannotUseTheCommandGetsAccessDeniedRatherThanWorkerNotAssigned() {
        Scene scene = assigned();

        ResponseEntity<String> response = post(scene.request(), "start",
                "{\"version\":" + version(scene.request()) + "}", scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED")
                .doesNotContain("WORKER_NOT_ASSIGNED");
    }

    @Test
    void holdAndRejectRequireAReasonWhileOthersDoNot() {
        Scene scene = assigned();
        post(scene.request(), "start", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());

        ResponseEntity<String> noReason = post(scene.request(), "hold",
                "{\"version\":" + version(scene.request()) + "}", scene.worker());
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noReason.getBody()).contains("VALIDATION_FAILED");
        assertThat(status(scene.request())).isEqualTo("IN_PROGRESS");

        Scene reported = reported();
        ResponseEntity<String> rejectNoReason = post(reported.request(), "reject",
                "{\"version\":" + version(reported.request()) + "}", reported.admin());
        assertThat(rejectNoReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(reported.request())).isEqualTo("REPORTED");
    }

    @Test
    void theReporterClosesAndReopensAResolvedRequest() {
        Scene scene = resolved();

        ResponseEntity<String> reopen = post(scene.request(), "reopen",
                "{\"statusReason\":\"still leaking\",\"version\":" + version(scene.request()) + "}",
                scene.resident());
        assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(scene.request())).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select resolved_at from maintenance_request where id=?",
                Instant.class, scene.request())).isNull();

        post(scene.request(), "resolve", "{\"version\":" + version(scene.request()) + "}",
                scene.worker());
        ResponseEntity<String> close = post(scene.request(), "close",
                "{\"version\":" + version(scene.request()) + "}", scene.resident());
        assertThat(close.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(scene.request())).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject(
                "select closed_at is not null from maintenance_request where id=?", Boolean.class,
                scene.request())).isTrue();
    }

    @Test
    void closeRejectAndMarkDuplicateEndTheActiveAssignmentWhileResolveKeepsIt() {
        Scene resolved = resolved();
        assertThat(activeAssignments(resolved.request())).isEqualTo(1);

        post(resolved.request(), "close", "{\"version\":" + version(resolved.request()) + "}",
                resolved.resident());
        assertThat(activeAssignments(resolved.request())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from maintenance_assignment where request_id=? and unassigned_by_id=?",
                Integer.class, resolved.request(), resolved.residentId())).isEqualTo(1);

        // reject and mark-duplicate only leave REPORTED, and a REPORTED request has never been
        // assigned, so in practice close is the only terminal transition with an assignment to
        // end. These two still must not leave one behind.
        Scene rejected = reported();
        assertThat(activeAssignments(rejected.request())).isZero();
        post(rejected.request(), "reject",
                "{\"statusReason\":\"not a fault\",\"version\":" + version(rejected.request())
                        + "}",
                rejected.admin());
        assertThat(status(rejected.request())).isEqualTo("REJECTED");
        assertThat(activeAssignments(rejected.request())).isZero();

        Scene duplicate = reported();
        markDuplicate(duplicate.request(), request(duplicate.residentId(), duplicate.room()),
                version(duplicate.request()), duplicate.admin());
        assertThat(status(duplicate.request())).isEqualTo("DUPLICATE");
        assertThat(activeAssignments(duplicate.request())).isZero();

        // An ASSIGNED request cannot be rejected at all: the frozen matrix limits reject to
        // REPORTED, so privilege does not open a shortcut around the assignment.
        Scene assignedRequest = assigned();
        ResponseEntity<String> refused = post(assignedRequest.request(), "reject",
                "{\"statusReason\":\"not a fault\",\"version\":"
                        + version(assignedRequest.request()) + "}",
                assignedRequest.admin());
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(assignedRequest.request())).isEqualTo("ASSIGNED");
        assertThat(activeAssignments(assignedRequest.request())).isEqualTo(1);
    }

    @Test
    void reopenNeedsAnActiveAssignmentToReturnInto() {
        Scene scene = resolved();
        jdbc.update("update maintenance_assignment set unassigned_at=current_timestamp,"
                + " unassigned_by_id=? where request_id=? and unassigned_at is null",
                scene.adminId(), scene.request());

        ResponseEntity<String> response = post(scene.request(), "reopen",
                "{\"version\":" + version(scene.request()) + "}", scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("INVALID_REQUEST_STATE");
        assertThat(status(scene.request())).isEqualTo("RESOLVED");
    }

    @Test
    void markDuplicateRefusesSelfReferenceAChainAndAnInvisibleOriginal() {
        Scene scene = reported();
        Scene elsewhere = reported();

        ResponseEntity<String> itself = markDuplicate(scene.request(), scene.request(),
                version(scene.request()), scene.admin());
        assertThat(itself.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(itself.getBody()).contains("INVALID_REQUEST_STATE");

        ResponseEntity<String> invisible = markDuplicate(scene.request(), elsewhere.request(),
                version(scene.request()), scene.admin());
        assertThat(invisible.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(invisible.getBody()).contains("REQUEST_NOT_FOUND");

        ResponseEntity<String> missing = markDuplicate(scene.request(), 999999L,
                version(scene.request()), scene.admin());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Build a real duplicate in the same dormitory, then try to chain onto it.
        long original = request(scene.residentId(), scene.room());
        Scene chainTarget = reported();
        long duplicate = request(scene.residentId(), scene.room());
        assertThat(markDuplicate(duplicate, original, version(duplicate), scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        long chainAttempt = request(scene.residentId(), scene.room());
        ResponseEntity<String> chain = markDuplicate(chainAttempt, duplicate,
                version(chainAttempt), scene.admin());
        assertThat(chain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(chain.getBody()).contains("INVALID_REQUEST_STATE");
        assertThat(status(chainTarget.request())).isEqualTo("REPORTED");
    }

    @Test
    void markDuplicateStoresTheOriginalAndRecordsItInMetadata() {
        Scene scene = reported();
        long original = request(scene.residentId(), scene.room());

        assertThat(markDuplicate(scene.request(), original, version(scene.request()), scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(status(scene.request())).isEqualTo("DUPLICATE");
        assertThat(jdbc.queryForObject("select duplicate_of_id from maintenance_request where id=?",
                Long.class, scene.request())).isEqualTo(original);
        assertThat(jdbc.queryForObject(
                "select metadata->>'duplicateOfId' from request_history where request_id=?"
                        + " and event_type='REQUEST_MARKED_DUPLICATE'",
                String.class, scene.request())).isEqualTo(Long.toString(original));
    }

    @Test
    void terminalStatesRefuseEveryFurtherCommandIncludingForASuperAdmin() {
        Scene scene = resolved();
        post(scene.request(), "close", "{\"version\":" + version(scene.request()) + "}",
                scene.resident());
        HttpHeaders superAdmin = headers(user("SUPER_ADMIN"), "SUPER_ADMIN");

        // Authorization is evaluated before the state check. A SUPER_ADMIN holds no WORKER role,
        // so the four worker commands stop at ACCESS_DENIED and never reach the state conflict.
        for (String workerCommand : List.of("start", "hold", "resume", "resolve")) {
            String body = "{\"statusReason\":\"x\",\"version\":" + version(scene.request()) + "}";
            ResponseEntity<String> response =
                    post(scene.request(), workerCommand, body, superAdmin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).contains("ACCESS_DENIED");
        }
        // The commands a SUPER_ADMIN may use reach the state check and conflict there, which is
        // what proves the terminal state is not bypassed by privilege.
        for (String adminCommand : List.of("close", "reopen", "reject")) {
            String body = "{\"statusReason\":\"x\",\"version\":" + version(scene.request()) + "}";
            ResponseEntity<String> response = post(scene.request(), adminCommand, body, superAdmin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).contains("INVALID_REQUEST_STATE");
        }
        assertThat(markDuplicate(scene.request(), request(scene.residentId(), scene.room()),
                version(scene.request()), superAdmin).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(scene.request())).isEqualTo("CLOSED");
    }

    @Test
    void aStaleVersionConflictsAndChangesNothing() {
        Scene scene = assigned();

        ResponseEntity<String> response =
                post(scene.request(), "start", "{\"version\":99}", scene.worker());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("VERSION_CONFLICT");
        assertThat(status(scene.request())).isEqualTo("ASSIGNED");
    }

    @Test
    void anOutOfScopeAdminCannotRejectAndAnInvisibleRequestIsNotFound() {
        Scene scene = reported();
        long otherAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                otherAdmin, dormitory());

        ResponseEntity<String> response = post(scene.request(), "reject",
                "{\"statusReason\":\"no\",\"version\":" + version(scene.request()) + "}",
                headers(otherAdmin, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
        assertThat(status(scene.request())).isEqualTo("REPORTED");
        assertThat(post(999999L, "reject", "{\"statusReason\":\"no\",\"version\":0}",
                scene.admin()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aTransitionAndItsHistoryRollBackTogether() {
        Scene scene = assigned();
        doThrow(new IllegalStateException("history write failed")).when(historyRepository)
                .save(any(RequestHistory.class));

        ResponseEntity<String> response = post(scene.request(), "start",
                "{\"version\":" + version(scene.request()) + "}", scene.worker());

        assertThat(response.getStatusCode().isError()).isTrue();
        assertThat(status(scene.request())).isEqualTo("ASSIGNED");
        assertThat(events(scene.request())).doesNotContain("REQUEST_STARTED");
    }

    @Test
    void unknownFieldsAndMissingVersionAreBadRequests() {
        Scene scene = assigned();

        assertThat(post(scene.request(), "start", "{\"version\":0,\"nope\":true}", scene.worker())
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(scene.request(), "start", "{}", scene.worker()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private List<String> events(long request) {
        return jdbc.queryForList(
                "select event_type from request_history where request_id=? order by id", String.class,
                request);
    }

    private int activeAssignments(long request) {
        return jdbc.queryForObject(
                "select count(*) from maintenance_assignment where request_id=? and unassigned_at is null",
                Integer.class, request);
    }

    private String status(long request) {
        return jdbc.queryForObject("select status from maintenance_request where id=?", String.class,
                request);
    }

    private String reason(long request) {
        return jdbc.queryForObject("select status_reason from maintenance_request where id=?",
                String.class, request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private ResponseEntity<String> post(long request, String command, String body,
            HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/" + command,
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> markDuplicate(long request, long original, long version,
            HttpHeaders headers) {
        return post(request, "mark-duplicate",
                "{\"duplicateOfId\":" + original + ",\"version\":" + version + "}", headers);
    }

    private ResponseEntity<String> assign(long request, long worker, long version,
            HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/assignments",
                HttpMethod.POST,
                new HttpEntity<>("{\"workerId\":" + worker + ",\"version\":" + version + "}",
                        headers),
                String.class);
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
        return new Scene(room, resident, request, admin, worker, headers(resident, "RESIDENT"),
                headers(admin, "ADMIN"), headers(worker, "WORKER"));
    }

    private Scene assigned() {
        Scene scene = reported();
        assign(scene.request(), scene.workerId(), version(scene.request()), scene.admin());
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

    private record Scene(long room, long residentId, long request, long adminId, long workerId,
            HttpHeaders resident, HttpHeaders admin, HttpHeaders worker) {
    }
}

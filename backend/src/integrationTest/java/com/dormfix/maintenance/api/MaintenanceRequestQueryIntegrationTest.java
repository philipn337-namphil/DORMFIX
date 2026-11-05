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
class MaintenanceRequestQueryIntegrationTest {
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
    void aResidentReadsTheirOwnRequestAndSomeoneElseRequestIsNotFoundRatherThanForbidden() {
        Scene scene = scene();

        ResponseEntity<String> own = get("/" + scene.ownRequest(), scene.resident());
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(own.getBody()).contains("DF-").contains("Ceiling leak")
                .contains("RESIDENT_PRESENT_REQUIRED");

        ResponseEntity<String> other = get("/" + scene.otherRequest(), scene.resident());
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.getBody()).contains("REQUEST_NOT_FOUND").doesNotContain("ACCESS_DENIED");
    }

    @Test
    void aMissingRequestAndAnInvisibleRequestAnswerIdentically() {
        Scene scene = scene();

        ResponseEntity<String> missing = get("/999999", scene.resident());
        ResponseEntity<String> invisible = get("/" + scene.otherRequest(), scene.resident());

        assertThat(missing.getStatusCode()).isEqualTo(invisible.getStatusCode());
        assertThat(missing.getBody()).contains("REQUEST_NOT_FOUND");
        assertThat(invisible.getBody()).contains("REQUEST_NOT_FOUND");
    }

    @Test
    void anAdminSeesOnlyTheirScopeAndASuperAdminSeesEverything() {
        Scene scene = scene();

        ResponseEntity<String> inScope = get("/" + scene.ownRequest(), scene.scopedAdmin());
        assertThat(inScope.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> outOfScope = get("/" + scene.otherRequest(), scene.scopedAdmin());
        assertThat(outOfScope.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(get("/" + scene.otherRequest(), scene.superAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aWorkerKeepsReadAccessAfterTheAssignmentEnds() {
        Scene scene = scene();
        long worker = user("WORKER");
        assign(scene.ownRequest(), worker, null);

        assertThat(get("/" + scene.ownRequest(), headers(worker, "WORKER")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        jdbc.update("update maintenance_assignment set unassigned_at=current_timestamp,"
                + " unassigned_by_id=? where request_id=? and worker_id=?", scene.superAdminId(),
                scene.ownRequest(), worker);

        assertThat(get("/" + scene.ownRequest(), headers(worker, "WORKER")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void theListingIsBoundedByVisibilityForEachRole() {
        Scene scene = scene();

        // Tests share one database, so a global SUPER_ADMIN count is not stable. Assert which
        // specific requests each role can reach instead.
        assertThat(totalElements(get("", scene.resident()))).isEqualTo(1);
        assertThat(get("", scene.resident()).getBody()).contains("\"id\":" + scene.ownRequest())
                .doesNotContain("\"id\":" + scene.otherRequest());
        assertThat(totalElements(get("", scene.scopedAdmin()))).isEqualTo(1);
        assertThat(get("", scene.scopedAdmin()).getBody()).contains("\"id\":" + scene.ownRequest())
                .doesNotContain("\"id\":" + scene.otherRequest());
        assertThat(get("?size=100", scene.superAdmin()).getBody())
                .contains("\"id\":" + scene.ownRequest())
                .contains("\"id\":" + scene.otherRequest());
    }

    @Test
    void theListingProjectionOmitsDescriptionAndEntryFields() {
        Scene scene = scene();

        String body = get("", scene.resident()).getBody();

        assertThat(body).contains("requestNumber").contains("assigneeId").contains("version")
                .doesNotContain("Ceiling leak").doesNotContain("entryPolicy")
                .doesNotContain("contactBeforeEntry");
    }

    @Test
    void theListingExposesTheActiveAssigneeAndOrdersNewestFirst() {
        Scene scene = scene();
        long worker = user("WORKER");
        assign(scene.ownRequest(), worker, null);
        long second = request(scene.residentId(), scene.room());

        String body = get("", scene.resident()).getBody();

        assertThat(body).contains("\"assigneeId\":" + worker);
        assertThat(body.indexOf("\"id\":" + second)).isLessThan(body.indexOf("\"id\":" + scene.ownRequest()));
    }

    @Test
    void filtersNarrowWithinVisibilityAndNeverWidenIt() {
        Scene scene = scene();

        assertThat(totalElements(get("?status=REPORTED", scene.resident()))).isEqualTo(1);
        assertThat(totalElements(get("?status=CLOSED", scene.resident()))).isZero();
        assertThat(totalElements(get("?spaceId=" + scene.otherRoom(), scene.resident()))).isZero();
        assertThat(totalElements(get("?priority=HIGH", scene.resident()))).isEqualTo(1);
        assertThat(get("?status=REPORTED&status=CLOSED&size=100", scene.superAdmin()).getBody())
                .contains("\"id\":" + scene.ownRequest())
                .contains("\"id\":" + scene.otherRequest());
        assertThat(get("?status=CLOSED&size=100", scene.superAdmin()).getBody())
                .contains("\"id\":" + scene.otherRequest())
                .doesNotContain("\"id\":" + scene.ownRequest());
    }

    @Test
    void anAdminNamingALocationOutsideItsScopeIsForbidden() {
        Scene scene = scene();

        assertThat(get("?spaceId=" + scene.otherRoom(), scene.scopedAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("?dormitoryId=" + scene.otherDormitory(), scene.scopedAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("?spaceId=" + scene.otherRoom(), scene.superAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aScopelessAdminCannotList() {
        assertThat(get("", headers(user("ADMIN"), "ADMIN")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void paginationBoundsAndUnparsableFiltersAreBadRequests() {
        Scene scene = scene();

        assertThat(get("?size=0", scene.superAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("?size=101", scene.superAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("?page=-1", scene.superAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> status = get("?status=NOPE", scene.superAdmin());
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status.getBody()).contains("VALIDATION_FAILED");
        assertThat(get("?priority=NOPE", scene.superAdmin()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignedToMeSelectsCurrentThenEndedAssignmentsAndIsWorkerOnly() {
        Scene scene = scene();
        long worker = user("WORKER");
        assign(scene.ownRequest(), worker, null);

        assertThat(totalElements(get("/assigned-to-me", headers(worker, "WORKER")))).isEqualTo(1);
        assertThat(totalElements(get("/assigned-to-me?active=false", headers(worker, "WORKER"))))
                .isZero();

        jdbc.update("update maintenance_assignment set unassigned_at=current_timestamp,"
                + " unassigned_by_id=? where request_id=? and worker_id=?", scene.superAdminId(),
                scene.ownRequest(), worker);

        assertThat(totalElements(get("/assigned-to-me", headers(worker, "WORKER")))).isZero();
        assertThat(totalElements(get("/assigned-to-me?active=false", headers(worker, "WORKER"))))
                .isEqualTo(1);
        assertThat(get("/assigned-to-me", scene.resident()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private int totalElements(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int marker = body.lastIndexOf("\"totalElements\":");
        String tail = body.substring(marker + "\"totalElements\":".length());
        return Integer.parseInt(tail.split("[,}]")[0].trim());
    }

    private ResponseEntity<String> get(String suffix, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests" + suffix, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private void assign(long request, long worker, String reason) {
        jdbc.update(
                "insert into maintenance_assignment(request_id,worker_id,assigned_by_id,reason)"
                        + " values (?,?,?,?)",
                request, worker, worker, reason);
        jdbc.update("update maintenance_request set status='ASSIGNED' where id=?", request);
    }

    private Scene scene() {
        long dormitory = dormitory();
        long room = space(dormitory);
        long resident = user("RESIDENT");
        long ownRequest = request(resident, room);

        long otherDormitory = dormitory();
        long otherRoom = space(otherDormitory);
        long otherResident = user("RESIDENT");
        long otherRequest = request(otherResident, otherRoom);
        jdbc.update("update maintenance_request set status='CLOSED' where id=?", otherRequest);

        long scopedAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                scopedAdmin, dormitory);
        long superAdmin = user("SUPER_ADMIN");

        return new Scene(room, otherRoom, otherDormitory, resident, ownRequest, otherRequest,
                superAdmin, headers(resident, "RESIDENT"), headers(scopedAdmin, "ADMIN"),
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

    private record Scene(long room, long otherRoom, long otherDormitory, long residentId,
            long ownRequest, long otherRequest, long superAdminId, HttpHeaders resident,
            HttpHeaders scopedAdmin, HttpHeaders superAdmin) {
    }
}

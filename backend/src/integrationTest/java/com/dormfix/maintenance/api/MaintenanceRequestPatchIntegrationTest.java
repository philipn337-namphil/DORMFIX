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
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MaintenanceRequestPatchIntegrationTest {
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

    @BeforeEach
    void enablePatch() {
        // The default simple request factory cannot send PATCH.
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void theReporterEditsTheirOwnFieldsAndTheAuditNamesFieldsWithoutValues() {
        Scene scene = scene();

        ResponseEntity<String> response = patch(scene.request(),
                "{\"title\":\"Bigger leak\",\"entryPolicy\":\"ABSENT_ENTRY_ALLOWED\",\"version\":0}",
                scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Bigger leak").contains("ABSENT_ENTRY_ALLOWED")
                .contains("\"version\":1");
        String metadata = jdbc.queryForObject(
                "select metadata::text from request_history where request_id=?"
                        + " and event_type='REQUEST_METADATA_UPDATED'",
                String.class, scene.request());
        assertThat(metadata).contains("title").contains("entryPolicy")
                .doesNotContain("Bigger leak").doesNotContain("ABSENT_ENTRY_ALLOWED");
    }

    @Test
    void aResidentCannotReachPriorityCategoryFacilityStatusOrSpace() {
        Scene scene = scene();

        for (String field : List.of("\"priority\":\"EMERGENCY\"",
                "\"categoryId\":" + scene.category(), "\"facilityId\":" + scene.facility(),
                "\"status\":\"CLOSED\"", "\"spaceId\":" + scene.otherRoom(),
                "\"reporterId\":1")) {
            ResponseEntity<String> response =
                    patch(scene.request(), "{" + field + ",\"version\":0}", scene.resident());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("VALIDATION_FAILED");
        }
        assertThat(column(scene.request(), "priority")).isEqualTo("HIGH");
        assertThat(column(scene.request(), "status")).isEqualTo("REPORTED");
        assertThat(version(scene.request())).isZero();
    }

    @Test
    void anAdministratorCannotReachEntryConsentOrTheReportText() {
        Scene scene = scene();

        for (String field : List.of("\"entryPolicy\":\"ABSENT_ENTRY_ALLOWED\"",
                "\"contactBeforeEntry\":false", "\"title\":\"x\"", "\"description\":\"y\"",
                "\"spaceId\":" + scene.otherRoom(), "\"status\":\"CLOSED\"")) {
            ResponseEntity<String> response =
                    patch(scene.request(), "{" + field + ",\"version\":0}", scene.admin());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(column(scene.request(), "entry_policy"))
                .isEqualTo("RESIDENT_PRESENT_REQUIRED");
        assertThat(jdbc.queryForObject(
                "select contact_before_entry from maintenance_request where id=?", Boolean.class,
                scene.request())).isTrue();
    }

    @Test
    void aResidentEditsOnlyWhileReportedAndOnlyTheirOwnRequest() {
        Scene scene = scene();
        Scene other = scene();

        ResponseEntity<String> foreign =
                patch(other.request(), "{\"title\":\"x\",\"version\":0}", scene.resident());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(foreign.getBody()).contains("ACCESS_DENIED");

        jdbc.update("update maintenance_request set status='ASSIGNED' where id=?",
                scene.request());
        ResponseEntity<String> late =
                patch(scene.request(), "{\"title\":\"x\",\"version\":0}", scene.resident());
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(late.getBody()).contains("INVALID_REQUEST_STATE");
        assertThat(column(scene.request(), "title")).isEqualTo("Leak");
    }

    @Test
    void anAdministratorReclassifiesAndCategoryAndPriorityAuditSeparately() {
        Scene scene = scene();
        long newCategory = category("LOW");

        ResponseEntity<String> response = patch(scene.request(),
                "{\"categoryId\":" + newCategory + ",\"priority\":\"EMERGENCY\",\"version\":0}",
                scene.admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"priority\":\"EMERGENCY\"");
        assertThat(events(scene.request())).contains("CATEGORY_CHANGED", "PRIORITY_CHANGED")
                .doesNotContain("REQUEST_METADATA_UPDATED");
    }

    @Test
    void changingTheCategoryNeverResetsThePriority() {
        Scene scene = scene();
        long lowCategory = category("LOW");

        patch(scene.request(), "{\"categoryId\":" + lowCategory + ",\"version\":0}",
                scene.admin());

        assertThat(column(scene.request(), "priority")).isEqualTo("HIGH");
        assertThat(events(scene.request())).contains("CATEGORY_CHANGED")
                .doesNotContain("PRIORITY_CHANGED");
    }

    @Test
    void theFacilityCanBeSetClearedAndIsValidatedAgainstTheRequestSpace() {
        Scene scene = scene();

        assertThat(patch(scene.request(),
                "{\"facilityId\":" + scene.facility() + ",\"version\":0}", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events(scene.request())).contains("REQUEST_METADATA_UPDATED");

        assertThat(patch(scene.request(), "{\"facilityId\":null,\"version\":1}", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select facility_id from maintenance_request where id=?",
                Long.class, scene.request())).isNull();

        long foreign = facility(scene.otherRoom(), "ACTIVE");
        ResponseEntity<String> wrongSpace = patch(scene.request(),
                "{\"facilityId\":" + foreign + ",\"version\":2}", scene.admin());
        assertThat(wrongSpace.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        long retired = facility(scene.room(), "RETIRED");
        assertThat(patch(scene.request(), "{\"facilityId\":" + retired + ",\"version\":2}",
                scene.admin()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        long outOfService = facility(scene.room(), "OUT_OF_SERVICE");
        assertThat(patch(scene.request(),
                "{\"facilityId\":" + outOfService + ",\"version\":2}", scene.admin())
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> missing =
                patch(scene.request(), "{\"facilityId\":999999,\"version\":3}", scene.admin());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("FACILITY_NOT_FOUND");
    }

    @Test
    void aMissingOrInactiveCategoryAndABadPriorityAreRejected() {
        Scene scene = scene();

        ResponseEntity<String> missing =
                patch(scene.request(), "{\"categoryId\":999999,\"version\":0}", scene.admin());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("CATEGORY_NOT_FOUND");

        long inactive = category("LOW");
        jdbc.update("update maintenance_category set active=false where id=?", inactive);
        assertThat(patch(scene.request(), "{\"categoryId\":" + inactive + ",\"version\":0}",
                scene.admin()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(patch(scene.request(), "{\"priority\":\"URGENTEST\",\"version\":0}",
                scene.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAdministratorNeedsScopeAndAnActiveRequest() {
        Scene scene = scene();
        long otherAdmin = user("ADMIN");
        jdbc.update("insert into admin_dormitory_scopes(user_id,dormitory_id) values (?,?)",
                otherAdmin, dormitory());

        ResponseEntity<String> outOfScope = patch(scene.request(),
                "{\"priority\":\"LOW\",\"version\":0}", headers(otherAdmin, "ADMIN"));
        assertThat(outOfScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(patch(scene.request(), "{\"priority\":\"LOW\",\"version\":0}",
                headers(user("SUPER_ADMIN"), "SUPER_ADMIN")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        for (String blocked : List.of("RESOLVED", "CLOSED", "REJECTED")) {
            Scene blockedScene = scene();
            if ("CLOSED".equals(blocked)) {
                jdbc.update("update maintenance_request set status='CLOSED',"
                        + " resolved_at=current_timestamp, closed_at=current_timestamp where id=?",
                        blockedScene.request());
            } else {
                jdbc.update("update maintenance_request set status=? where id=?", blocked,
                        blockedScene.request());
            }
            ResponseEntity<String> response = patch(blockedScene.request(),
                    "{\"priority\":\"LOW\",\"version\":0}", blockedScene.admin());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).contains("INVALID_REQUEST_STATE");
        }
    }

    @Test
    void thePreferredWindowIsSetClearedAndKeptAsAPair() {
        Scene scene = scene();

        ResponseEntity<String> half = patch(scene.request(),
                "{\"preferredVisitStart\":\"2026-11-11T10:00:00Z\",\"version\":0}",
                scene.resident());
        assertThat(half.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(half.getBody()).contains("INVALID_VISIT_TIME");

        assertThat(patch(scene.request(),
                "{\"preferredVisitStart\":\"2026-11-11T10:00:00Z\","
                        + "\"preferredVisitEnd\":\"2026-11-11T11:00:00Z\",\"version\":0}",
                scene.resident()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> inverted = patch(scene.request(),
                "{\"preferredVisitEnd\":\"2026-11-11T09:00:00Z\",\"version\":1}",
                scene.resident());
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(patch(scene.request(),
                "{\"preferredVisitStart\":null,\"preferredVisitEnd\":null,\"version\":1}",
                scene.resident()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                "select preferred_visit_start is null and preferred_visit_end is null"
                        + " from maintenance_request where id=?",
                Boolean.class, scene.request())).isTrue();
    }

    @Test
    void aStaleVersionConflictsAndAnUnchangedPatchLeavesVersionAndHistoryAlone() {
        Scene scene = scene();

        ResponseEntity<String> stale =
                patch(scene.request(), "{\"title\":\"x\",\"version\":99}", scene.resident());
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).contains("VERSION_CONFLICT");

        long historyBefore = historyCount(scene.request());
        ResponseEntity<String> unchanged =
                patch(scene.request(), "{\"title\":\"Leak\",\"version\":0}", scene.resident());
        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unchanged.getBody()).contains("\"version\":0");
        assertThat(version(scene.request())).isZero();
        assertThat(historyCount(scene.request())).isEqualTo(historyBefore);
    }

    @Test
    void aWorkerCannotPatchAndADualRoleCallerIsReadAsAnAdministrator() {
        Scene scene = scene();

        ResponseEntity<String> worker = patch(scene.request(),
                "{\"priority\":\"LOW\",\"version\":0}", headers(user("WORKER"), "WORKER"));
        assertThat(worker.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(worker.getBody()).contains("ACCESS_DENIED");

        // The reporter also holds SUPER_ADMIN: resident fields are now unknown to them.
        HttpHeaders dual = headers(scene.residentId(), "RESIDENT", "SUPER_ADMIN");
        assertThat(patch(scene.request(), "{\"title\":\"x\",\"version\":0}", dual)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patch(scene.request(), "{\"priority\":\"LOW\",\"version\":0}", dual)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aMissingRequestIsNotFound() {
        Scene scene = scene();

        ResponseEntity<String> response =
                patch(999999L, "{\"priority\":\"LOW\",\"version\":0}", scene.admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("REQUEST_NOT_FOUND");
    }

    private List<String> events(long request) {
        return jdbc.queryForList(
                "select event_type from request_history where request_id=? order by id",
                String.class, request);
    }

    private long historyCount(long request) {
        return jdbc.queryForObject("select count(*) from request_history where request_id=?",
                Long.class, request);
    }

    private String column(long request, String name) {
        return jdbc.queryForObject("select " + name + " from maintenance_request where id=?",
                String.class, request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private ResponseEntity<String> patch(long request, String body, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request, HttpMethod.PATCH,
                new HttpEntity<>(body, headers), String.class);
    }

    private Scene scene() {
        long dormitory = dormitory();
        long room = space(dormitory);
        long otherRoom = space(dormitory);
        long resident = user("RESIDENT");
        long category = category("HIGH");
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
        return new Scene(request, room, otherRoom, resident, category, facility(room, "ACTIVE"),
                headers(resident, "RESIDENT"), headers(admin, "ADMIN"));
    }

    private long category(String defaultPriority) {
        return jdbc.queryForObject(
                "insert into maintenance_category(code,name,default_priority,sort_order)"
                        + " values (?,?,?,1) returning id",
                Long.class, key(), "Category", defaultPriority);
    }

    private long facility(long space, String status) {
        return jdbc.queryForObject(
                "insert into facility(space_id,name,facility_type,status) values (?,?,'SINK',?)"
                        + " returning id",
                Long.class, space, key(), status);
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

    private record Scene(long request, long room, long otherRoom, long residentId, long category,
            long facility, HttpHeaders resident, HttpHeaders admin) {
    }
}

package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.test.FakeAttachmentStorage;
import com.dormfix.test.TestJwtKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class AttachmentIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        // Limits are configuration, so the test narrows one to keep the limit case small.
        registry.add("dormfix.attachments.max-attachments-per-request", () -> "3");
        TestJwtKeys.register(registry);
    }

    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        FakeAttachmentStorage fakeAttachmentStorage() {
            return new FakeAttachmentStorage();
        }
    }

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FakeAttachmentStorage storage;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theReporterPresignsUploadsAndRegistersAndTheRequestIsLeftUntouched() throws Exception {
        Scene scene = assigned();
        long historyBefore = historyCount(scene.request());
        long version = version(scene.request());

        ResponseEntity<String> presign = presign(scene.request(), "REPORT", "leak.png",
                "image/png", 1000, scene.resident());
        assertThat(presign.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = mapper.readTree(presign.getBody());
        String key = body.get("objectKey").asText();
        assertThat(key).matches("requests/" + scene.request() + "/attachments/"
                + scene.residentId() + "/[0-9a-f-]{36}");
        assertThat(key).doesNotContain("leak");
        assertThat(body.get("uploadUrl").asText()).startsWith("https://");
        assertThat(body.get("method").asText()).isEqualTo("PUT");
        assertThat(body.get("expiresAt").asText()).isNotEmpty();

        storage.upload(key, 1000, "image/png");
        ResponseEntity<String> registered = register(scene.request(), key, "REPORT", "leak.png",
                scene.resident());

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registered.getBody()).contains("leak.png").contains("image/png")
                .contains("\"fileSize\":1000").doesNotContain("objectKey").doesNotContain(key);
        assertThat(jdbc.queryForObject("select uploader_id from attachment where object_key=?",
                Long.class, key)).isEqualTo(scene.residentId());
        assertThat(version(scene.request())).isEqualTo(version);
        assertThat(historyCount(scene.request())).isEqualTo(historyBefore);
    }

    @Test
    void registrationRecordsWhatTheStoreSawNotWhatTheClientDeclared() throws Exception {
        Scene scene = assigned();
        String key = presignedKey(scene.request(), "REPORT", "image/png", 1000, scene.resident());

        // The client declared a 1000 byte PNG and uploaded a 2048 byte JPEG.
        storage.upload(key, 2048, "image/jpeg");
        ResponseEntity<String> registered = register(scene.request(), key, "REPORT", "a.png",
                scene.resident());

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registered.getBody()).contains("image/jpeg").contains("\"fileSize\":2048");
    }

    @Test
    void anUploadThatBreaksTheLimitsIsCaughtAtRegistrationEvenAfterAPassingPresign()
            throws Exception {
        Scene scene = assigned();

        String executable = presignedKey(scene.request(), "REPORT", "image/png", 1000,
                scene.resident());
        storage.upload(executable, 1000, "application/x-msdownload");
        ResponseEntity<String> wrongType = register(scene.request(), executable, "REPORT", "a.png",
                scene.resident());
        assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongType.getBody()).contains("INVALID_FILE_TYPE");

        String huge = presignedKey(scene.request(), "REPORT", "image/png", 1000,
                scene.resident());
        storage.upload(huge, 50L * 1024 * 1024, "image/png");
        ResponseEntity<String> tooLarge = register(scene.request(), huge, "REPORT", "a.png",
                scene.resident());
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLarge.getBody()).contains("FILE_TOO_LARGE");

        assertThat(attachmentCount(scene.request())).isZero();
    }

    @Test
    void anObjectThatWasNeverUploadedCannotBeRegistered() throws Exception {
        Scene scene = assigned();
        String key = presignedKey(scene.request(), "REPORT", "image/png", 1000, scene.resident());

        ResponseEntity<String> response = register(scene.request(), key, "REPORT", "a.png",
                scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("INVALID_REQUEST_STATE");
        assertThat(attachmentCount(scene.request())).isZero();
    }

    @Test
    void aKeyIssuedToSomeoneElseOrForAnotherRequestCannotBeRegistered() throws Exception {
        Scene scene = assigned();
        Scene other = assigned();

        // A key the worker received, presented by the resident.
        String workersKey = presignedKey(scene.request(), "OTHER", "image/png", 1000,
                scene.worker());
        storage.upload(workersKey, 1000, "image/png");
        ResponseEntity<String> stolen = register(scene.request(), workersKey, "OTHER", "a.png",
                scene.resident());
        assertThat(stolen.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stolen.getBody()).contains("ACCESS_DENIED");

        // A key the same resident received for a different request.
        String otherRequestKey = "requests/" + other.request() + "/attachments/"
                + scene.residentId() + "/" + UUID.randomUUID();
        storage.upload(otherRequestKey, 1000, "image/png");
        assertThat(register(scene.request(), otherRequestKey, "REPORT", "a.png",
                scene.resident()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        for (String forged : List.of("anything", "requests/" + scene.request() + "/attachments/"
                + scene.residentId() + "/../../x", "requests/" + scene.request()
                + "/attachments/" + scene.residentId() + "/photo.png")) {
            assertThat(register(scene.request(), forged, "REPORT", "a.png", scene.resident())
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(attachmentCount(scene.request())).isZero();
    }

    @Test
    void theSameObjectCannotBeRegisteredTwice() throws Exception {
        Scene scene = assigned();
        String key = presignedKey(scene.request(), "REPORT", "image/png", 1000, scene.resident());
        storage.upload(key, 1000, "image/png");
        register(scene.request(), key, "REPORT", "a.png", scene.resident());

        ResponseEntity<String> again = register(scene.request(), key, "REPORT", "a.png",
                scene.resident());

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(attachmentCount(scene.request())).isEqualTo(1);
    }

    @Test
    void eachRelationshipMayAddOnlyItsOwnTypes() {
        Scene scene = assigned();

        assertThat(status(presign(scene.request(), "REPORT", "a.png", "image/png", 10,
                scene.resident()))).isEqualTo(HttpStatus.OK);
        assertThat(status(presign(scene.request(), "OTHER", "a.png", "image/png", 10,
                scene.resident()))).isEqualTo(HttpStatus.OK);
        assertForbidden(presign(scene.request(), "BEFORE_REPAIR", "a.png", "image/png", 10,
                scene.resident()));
        assertForbidden(presign(scene.request(), "AFTER_REPAIR", "a.png", "image/png", 10,
                scene.resident()));

        for (String type : List.of("BEFORE_REPAIR", "AFTER_REPAIR", "OTHER")) {
            assertThat(status(presign(scene.request(), type, "a.png", "image/png", 10,
                    scene.worker()))).isEqualTo(HttpStatus.OK);
        }
        assertForbidden(presign(scene.request(), "REPORT", "a.png", "image/png", 10,
                scene.worker()));

        assertThat(status(presign(scene.request(), "OTHER", "a.png", "image/png", 10,
                scene.admin()))).isEqualTo(HttpStatus.OK);
        for (String type : List.of("REPORT", "BEFORE_REPAIR", "AFTER_REPAIR")) {
            assertForbidden(presign(scene.request(), type, "a.png", "image/png", 10,
                    scene.admin()));
        }
    }

    @Test
    void aFormerAssigneeAndAnUnrelatedCallerCannotAdd() {
        Scene scene = assigned();
        reassign(scene);

        ResponseEntity<String> former = presign(scene.request(), "AFTER_REPAIR", "a.png",
                "image/png", 10, scene.worker());
        assertThat(former.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(former.getBody()).contains("WORKER_NOT_ASSIGNED");

        ResponseEntity<String> stranger = presign(scene.request(), "REPORT", "a.png",
                "image/png", 10, headers(user("RESIDENT"), "RESIDENT"));
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stranger.getBody()).contains("REQUEST_NOT_FOUND");
    }

    @Test
    void theConfiguredLimitsAndFilenameRulesAreEnforcedAtPresign() {
        Scene scene = assigned();

        ResponseEntity<String> type = presign(scene.request(), "REPORT", "a.exe",
                "application/x-msdownload", 10, scene.resident());
        assertThat(type.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(type.getBody()).contains("INVALID_FILE_TYPE");

        for (long size : List.of(0L, -1L, 10L * 1024 * 1024 + 1)) {
            ResponseEntity<String> response = presign(scene.request(), "REPORT", "a.png",
                    "image/png", size, scene.resident());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("FILE_TOO_LARGE");
        }
        assertThat(status(presign(scene.request(), "REPORT", "a.png", "image/png",
                10L * 1024 * 1024, scene.resident()))).isEqualTo(HttpStatus.OK);

        for (String filename : List.of("../etc/passwd", "a/b.png", "a\\\\b.png")) {
            ResponseEntity<String> response = presign(scene.request(), "REPORT", filename,
                    "image/png", 10, scene.resident());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("VALIDATION_FAILED");
        }
        assertThat(status(presign(scene.request(), "NOPE", "a.png", "image/png", 10,
                scene.resident()))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registrationRefusesAClientSuppliedTypeOrSize() throws Exception {
        Scene scene = assigned();
        String key = presignedKey(scene.request(), "REPORT", "image/png", 1000, scene.resident());
        storage.upload(key, 1000, "image/png");

        ResponseEntity<String> response = post(
                "/api/v1/maintenance-requests/" + scene.request() + "/attachments",
                "{\"objectKey\":\"" + key + "\",\"type\":\"REPORT\",\"originalFilename\":\"a.png\","
                        + "\"contentType\":\"image/png\",\"fileSize\":1}",
                scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(attachmentCount(scene.request())).isZero();
    }

    @Test
    void attachmentsAreAcceptedUntilTheRequestIsTerminalIncludingResolved() throws Exception {
        Scene scene = assigned();
        command(scene.request(), "start", scene.worker());
        command(scene.request(), "resolve", scene.worker());

        String key = presignedKey(scene.request(), "AFTER_REPAIR", "image/jpeg", 500,
                scene.worker());
        storage.upload(key, 500, "image/jpeg");
        assertThat(status(register(scene.request(), key, "AFTER_REPAIR", "after.jpg",
                scene.worker()))).isEqualTo(HttpStatus.OK);

        // A key presigned while open, registered after the request closed.
        String late = presignedKey(scene.request(), "REPORT", "image/png", 10, scene.resident());
        storage.upload(late, 10, "image/png");
        command(scene.request(), "close", scene.resident());

        ResponseEntity<String> closedPresign = presign(scene.request(), "REPORT", "a.png",
                "image/png", 10, scene.resident());
        assertThat(closedPresign.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(closedPresign.getBody()).contains("INVALID_REQUEST_STATE");
        assertThat(status(register(scene.request(), late, "REPORT", "a.png", scene.resident())))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void theConfiguredPerRequestLimitIsEnforced() throws Exception {
        Scene scene = assigned();
        for (int index = 0; index < 3; index++) {
            String key = presignedKey(scene.request(), "REPORT", "image/png", 10,
                    scene.resident());
            storage.upload(key, 10, "image/png");
            assertThat(status(register(scene.request(), key, "REPORT", index + ".png",
                    scene.resident()))).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> fourth = presign(scene.request(), "REPORT", "4.png", "image/png",
                10, scene.resident());

        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fourth.getBody()).contains("INVALID_REQUEST_STATE");
    }

    @Test
    void everyReaderOfTheRequestSeesAllAttachmentsAndNeverAnObjectKey() throws Exception {
        Scene scene = assigned();
        String report = presignedKey(scene.request(), "REPORT", "image/png", 10,
                scene.resident());
        storage.upload(report, 10, "image/png");
        register(scene.request(), report, "REPORT", "first.png", scene.resident());
        String repair = presignedKey(scene.request(), "AFTER_REPAIR", "image/jpeg", 20,
                scene.worker());
        storage.upload(repair, 20, "image/jpeg");
        register(scene.request(), repair, "AFTER_REPAIR", "second.jpg", scene.worker());

        for (HttpHeaders reader : List.of(scene.resident(), scene.worker(), scene.admin())) {
            ResponseEntity<String> response = list(scene.request(), reader);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            String body = response.getBody();
            assertThat(body).contains("first.png").contains("second.jpg")
                    .contains("AFTER_REPAIR").doesNotContain("objectKey")
                    .doesNotContain("requests/");
            assertThat(body.indexOf("first.png")).isLessThan(body.indexOf("second.jpg"));
        }
        assertThat(list(scene.request(), headers(user("RESIDENT"), "RESIDENT")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aDownloadUrlIsIssuedToReadersAndAnUnreadableAttachmentLooksMissing() throws Exception {
        Scene scene = assigned();
        String key = presignedKey(scene.request(), "REPORT", "image/png", 10, scene.resident());
        storage.upload(key, 10, "image/png");
        long id = mapper.readTree(register(scene.request(), key, "REPORT", "a.png",
                scene.resident()).getBody()).get("id").asLong();

        for (HttpHeaders reader : List.of(scene.resident(), scene.worker(), scene.admin())) {
            ResponseEntity<String> response = downloadUrl(id, reader);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(mapper.readTree(response.getBody()).get("url").asText())
                    .startsWith("https://");
            assertThat(response.getBody()).contains("expiresAt");
        }

        ResponseEntity<String> hidden = downloadUrl(id, headers(user("RESIDENT"), "RESIDENT"));
        ResponseEntity<String> missing = downloadUrl(999999L, scene.resident());
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hidden.getStatusCode()).isEqualTo(missing.getStatusCode());
        assertThat(hidden.getBody()).contains("ATTACHMENT_NOT_FOUND")
                .doesNotContain("ACCESS_DENIED");
        assertThat(missing.getBody()).contains("ATTACHMENT_NOT_FOUND");
    }

    @Test
    void thereIsNoAttachmentDeleteOrUpdatePath() throws Exception {
        Scene scene = assigned();
        String key = presignedKey(scene.request(), "REPORT", "image/png", 10, scene.resident());
        storage.upload(key, 10, "image/png");
        long id = mapper.readTree(register(scene.request(), key, "REPORT", "a.png",
                scene.resident()).getBody()).get("id").asLong();

        for (HttpMethod method : List.of(HttpMethod.DELETE, HttpMethod.PUT)) {
            ResponseEntity<String> response = http.exchange("/api/v1/attachments/" + id, method,
                    new HttpEntity<>("{}", scene.admin()), String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
        }
        assertThat(attachmentCount(scene.request())).isEqualTo(1);
    }

    private void assertForbidden(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
    }

    private HttpStatus status(ResponseEntity<String> response) {
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private String presignedKey(long request, String type, String contentType, long size,
            HttpHeaders headers) throws Exception {
        ResponseEntity<String> response = presign(request, type, "a.png", contentType, size,
                headers);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).get("objectKey").asText();
    }

    private ResponseEntity<String> presign(long request, String type, String filename,
            String contentType, long size, HttpHeaders headers) {
        return post("/api/v1/maintenance-requests/" + request + "/attachments/presigned-url",
                "{\"type\":\"" + type + "\",\"originalFilename\":\"" + filename
                        + "\",\"contentType\":\"" + contentType + "\",\"fileSize\":" + size + "}",
                headers);
    }

    private ResponseEntity<String> register(long request, String key, String type,
            String filename, HttpHeaders headers) {
        return post("/api/v1/maintenance-requests/" + request + "/attachments",
                "{\"objectKey\":\"" + key + "\",\"type\":\"" + type
                        + "\",\"originalFilename\":\"" + filename + "\"}",
                headers);
    }

    private ResponseEntity<String> list(long request, HttpHeaders headers) {
        return http.exchange("/api/v1/maintenance-requests/" + request + "/attachments",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> downloadUrl(long attachment, HttpHeaders headers) {
        return http.exchange("/api/v1/attachments/" + attachment + "/download-url",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, String body, HttpHeaders headers) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private void command(long request, String command, HttpHeaders headers) {
        post("/api/v1/maintenance-requests/" + request + "/" + command,
                "{\"version\":" + version(request) + "}", headers);
    }

    private void reassign(Scene scene) {
        post("/api/v1/maintenance-requests/" + scene.request() + "/assignments",
                "{\"workerId\":" + user("WORKER") + ",\"version\":" + version(scene.request())
                        + "}",
                scene.admin());
    }

    private long attachmentCount(long request) {
        return jdbc.queryForObject("select count(*) from attachment where request_id=?",
                Long.class, request);
    }

    private long historyCount(long request) {
        return jdbc.queryForObject("select count(*) from request_history where request_id=?",
                Long.class, request);
    }

    private long version(long request) {
        return jdbc.queryForObject("select version from maintenance_request where id=?", Long.class,
                request);
    }

    private Scene assigned() {
        String name = key();
        long dormitory = jdbc.queryForObject(
                "insert into dormitory(name,address,timezone) values (?,?,'Asia/Seoul') returning id",
                Long.class, name, name);
        long building = jdbc.queryForObject(
                "insert into building(dormitory_id,code,name) values (?,?,?) returning id",
                Long.class, dormitory, key(), "B");
        long room = jdbc.queryForObject(
                "insert into space(building_id,code,name,type,floor) values (?,?,?,'ROOM',1) returning id",
                Long.class, building, key(), "R");
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
        Scene scene = new Scene(request, resident, headers(resident, "RESIDENT"),
                headers(admin, "ADMIN"), headers(worker, "WORKER"));
        post("/api/v1/maintenance-requests/" + request + "/assignments",
                "{\"workerId\":" + worker + ",\"version\":" + version(request) + "}",
                scene.admin());
        return scene;
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
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TestJwtKeys.ISSUER)
                .audience(List.of(TestJwtKeys.AUDIENCE)).subject(Long.toString(id))
                .issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300)).claim("roles", List.of(role)).build();
        RSAKey key = new RSAKey.Builder(TestJwtKeys.publicKey())
                .privateKey(TestJwtKeys.privateKey()).keyID(TestJwtKeys.KEY_ID)
                .algorithm(JWSAlgorithm.RS256).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        headers.setBearerAuth(encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build(), claims))
                .getTokenValue());
        return headers;
    }

    private record Scene(long request, long residentId, HttpHeaders resident, HttpHeaders admin,
            HttpHeaders worker) {
    }
}

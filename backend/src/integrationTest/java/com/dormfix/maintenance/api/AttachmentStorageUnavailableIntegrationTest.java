package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * The application as it actually ships in this slice: no object store is connected and the
 * fail-closed adapter is in effect. Nothing here installs a fake.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentStorageUnavailableIntegrationTest {
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

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void anAuthorizedPresignAnswers503InTheStandardErrorBody() throws Exception {
        Scene scene = scene();

        ResponseEntity<String> response = presign(scene.request(), "image/png", 10,
                scene.resident());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = mapper.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("traceId").asText()).isNotEmpty();
        assertThat(body.get("fieldErrors").isArray()).isTrue();
        assertThat(body.get("path").asText()).contains("/attachments/presigned-url");
    }

    @Test
    void validationAndAuthorizationStillAnswerBeforeStorageIsEverReached() {
        Scene scene = scene();

        // A caller who may not see the request learns nothing about storage being down.
        ResponseEntity<String> stranger = presign(scene.request(), "image/png", 10,
                headers(user("RESIDENT"), "RESIDENT"));
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(stranger.getBody()).contains("REQUEST_NOT_FOUND");

        ResponseEntity<String> badType = presign(scene.request(), "application/x-msdownload", 10,
                scene.resident());
        assertThat(badType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badType.getBody()).contains("INVALID_FILE_TYPE");

        ResponseEntity<String> forgedKey = http.exchange(
                "/api/v1/maintenance-requests/" + scene.request() + "/attachments",
                HttpMethod.POST,
                new HttpEntity<>("{\"objectKey\":\"anything\",\"type\":\"REPORT\","
                        + "\"originalFilename\":\"a.png\"}", scene.resident()),
                String.class);
        assertThat(forgedKey.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aWellFormedRegistrationCannotSucceedWithoutAStore() {
        Scene scene = scene();
        String key = "requests/" + scene.request() + "/attachments/" + scene.residentId() + "/"
                + UUID.randomUUID();

        ResponseEntity<String> response = http.exchange(
                "/api/v1/maintenance-requests/" + scene.request() + "/attachments",
                HttpMethod.POST,
                new HttpEntity<>("{\"objectKey\":\"" + key + "\",\"type\":\"REPORT\","
                        + "\"originalFilename\":\"a.png\"}", scene.resident()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(jdbc.queryForObject("select count(*) from attachment where request_id=?",
                Long.class, scene.request())).isZero();
    }

    @Test
    void listingNeedsNoStoreAndStillWorks() {
        Scene scene = scene();

        ResponseEntity<String> response = http.exchange(
                "/api/v1/maintenance-requests/" + scene.request() + "/attachments",
                HttpMethod.GET, new HttpEntity<>(scene.resident()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\":[]");
    }

    private ResponseEntity<String> presign(long request, String contentType, long size,
            HttpHeaders headers) {
        return http.exchange(
                "/api/v1/maintenance-requests/" + request + "/attachments/presigned-url",
                HttpMethod.POST,
                new HttpEntity<>("{\"type\":\"REPORT\",\"originalFilename\":\"a.png\","
                        + "\"contentType\":\"" + contentType + "\",\"fileSize\":" + size + "}",
                        headers),
                String.class);
    }

    private Scene scene() {
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
        return new Scene(request, resident, headers(resident, "RESIDENT"));
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

    private record Scene(long request, long residentId, HttpHeaders resident) {
    }
}

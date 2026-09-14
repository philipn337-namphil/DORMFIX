package com.dormfix;

import com.dormfix.test.TestJwtKeys;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        TestJwtKeys.register(registry);
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationIsValidAndRepeatableStartupHasNoPendingMigration() {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void authenticationMigrationCreatesExpectedSchemaObjects() {
        assertThat(tableNames()).containsExactly("app_user", "refresh_token_sessions", "user_roles");
        assertThat(columnNames("app_user")).containsExactly(
                "id", "email", "password_hash", "name", "phone", "student_number", "status",
                "last_login_at", "created_at", "updated_at");
        assertThat(columnNames("user_roles")).containsExactly("user_id", "role");
        assertThat(columnNames("refresh_token_sessions")).containsExactly(
                "id", "user_id", "token_hash", "expires_at", "revoked_at", "created_at");
        assertThat(notNullColumnNames("app_user")).containsExactly(
                "id", "email", "password_hash", "name", "status", "created_at", "updated_at");
        assertThat(notNullColumnNames("user_roles")).containsExactly("user_id", "role");
        assertThat(notNullColumnNames("refresh_token_sessions")).containsExactly(
                "id", "user_id", "token_hash", "expires_at", "created_at");

        assertThat(constraintNames()).contains(
                "pk_app_user", "uq_app_user_email", "uq_app_user_student_number",
                "ck_app_user_email_normalized", "ck_app_user_status",
                "pk_user_roles", "fk_user_roles_user", "ck_user_roles_role",
                "pk_refresh_token_sessions", "fk_refresh_token_sessions_user",
                "uq_refresh_token_sessions_token_hash", "ck_refresh_token_sessions_token_hash_length",
                "ck_refresh_token_sessions_expiry", "ck_refresh_token_sessions_revocation");
        assertThat(indexNames()).contains(
                "pk_app_user", "uq_app_user_email", "uq_app_user_student_number",
                "pk_user_roles", "ix_user_roles_role",
                "pk_refresh_token_sessions", "uq_refresh_token_sessions_token_hash",
                "ix_refresh_token_sessions_user_id");
    }

    @Test
    void appUserConstraintsRejectInvalidOrDuplicateIdentityData() {
        String email = uniqueEmail();
        String studentNumber = uniqueStudentNumber();
        insertUser(email, studentNumber);

        assertThatThrownBy(() -> insertUser(email, uniqueStudentNumber()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser(uniqueEmail(), studentNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser(" Not-Normalized@Example.com ", uniqueStudentNumber()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (email, password_hash, name, status, created_at, updated_at)
                VALUES (?, 'hash', 'Resident', 'UNKNOWN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, uniqueEmail()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (email, password_hash, name, status, created_at, updated_at)
                VALUES (?, 'hash', NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, uniqueEmail()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userRoleConstraintsRejectDuplicatesInvalidRolesAndOrphans() {
        long userId = insertUser(uniqueEmail(), null);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'RESIDENT')", userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_roles (user_id, role) VALUES (?, 'RESIDENT')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_roles (user_id, role) VALUES (?, 'UNKNOWN')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_roles (user_id, role) VALUES (?, 'RESIDENT')", Long.MAX_VALUE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refreshTokenConstraintsRejectDuplicateMalformedOrInvalidSessions() {
        long userId = insertUser(uniqueEmail(), null);
        byte[] tokenHash = hashWithLeadingByte(0);
        insertRefreshToken(userId, tokenHash);

        assertThatThrownBy(() -> insertRefreshToken(userId, tokenHash))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefreshToken(userId, new byte[31]))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO refresh_token_sessions
                    (user_id, token_hash, expires_at, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP - INTERVAL '1 second', CURRENT_TIMESTAMP)
                """, userId, hashWithLeadingByte(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO refresh_token_sessions
                    (user_id, token_hash, expires_at, revoked_at, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                        CURRENT_TIMESTAMP - INTERVAL '1 second', CURRENT_TIMESTAMP)
                """, userId, hashWithLeadingByte(2)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRefreshToken(Long.MAX_VALUE, hashWithLeadingByte(3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void safeHealthProbesWorkWithRealPostgres() {
        for (String probe : new String[] {"liveness", "readiness"}) {
            var response = http.getForEntity("/actuator/health/" + probe, Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("status", "UP");
            assertThat(response.getBody()).doesNotContainKey("components");
        }
    }

    @Test
    void applicationDoesNotExposeBusinessOrSensitiveActuatorEndpoints() {
        assertThat(http.getForEntity("/api/v1/me", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private List<String> tableNames() {
        return jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('app_user', 'user_roles', 'refresh_token_sessions')
                ORDER BY table_name
                """, String.class);
    }

    private List<String> columnNames(String tableName) {
        return jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, tableName);
    }

    private List<String> constraintNames() {
        return jdbc.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name IN ('app_user', 'user_roles', 'refresh_token_sessions')
                """, String.class);
    }

    private List<String> notNullColumnNames(String tableName) {
        return jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND is_nullable = 'NO'
                ORDER BY ordinal_position
                """, String.class, tableName);
    }

    private List<String> indexNames() {
        return jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename IN ('app_user', 'user_roles', 'refresh_token_sessions')
                """, String.class);
    }

    private long insertUser(String email, String studentNumber) {
        return jdbc.queryForObject("""
                INSERT INTO app_user
                    (email, password_hash, name, student_number, status, created_at, updated_at)
                VALUES (?, 'hash', 'Resident', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, email, studentNumber);
    }

    private void insertRefreshToken(long userId, byte[] tokenHash) {
        jdbc.update("""
                INSERT INTO refresh_token_sessions (user_id, token_hash, expires_at, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '14 days', CURRENT_TIMESTAMP)
                """, userId, tokenHash);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String uniqueStudentNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private byte[] hashWithLeadingByte(int value) {
        byte[] hash = new byte[32];
        hash[0] = (byte) value;
        return hash;
    }
}

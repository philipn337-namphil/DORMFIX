package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.RefreshTokenSessionRepository;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.RefreshTokenSession;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUserRepositoryAdapter.class, JpaRefreshTokenSessionRepositoryAdapter.class})
class IdentityRepositoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenSessionRepository refreshTokenSessions;
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesUserAndFindsByNormalizedEmail() {
        User saved = users.save(newUser("resident@example.com"));
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        assertThat(users.existsByEmail("resident@example.com")).isTrue();
        assertThat(users.existsByEmail("missing@example.com")).isFalse();
        assertThat(users.findByEmail("resident@example.com"))
                .get()
                .satisfies(user -> {
                    assertThat(user.getEmail()).isEqualTo("resident@example.com");
                    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    assertThat(user.getRoles()).containsExactly(Role.RESIDENT);
                });
    }

    @Test
    void savesRefreshSessionAndFindsByBinaryTokenHash() {
        User user = users.save(newUser("token-owner@example.com"));
        byte[] tokenHash = hashWithLeadingByte(1);
        RefreshTokenSession saved = refreshTokenSessions.save(
                new RefreshTokenSession(user.getId(), tokenHash, NOW.plusSeconds(1_200), NOW));
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        assertThat(refreshTokenSessions.findByTokenHash(tokenHash))
                .get()
                .satisfies(session -> {
                    assertThat(session.getUserId()).isEqualTo(user.getId());
                    assertThat(session.getTokenHash()).containsExactly(tokenHash);
                    assertThat(session.getRevokedAt()).isNull();
                });
    }

    @Test
    void findsOnlyActiveRefreshSessionsForRequestedUser() {
        User firstUser = users.save(newUser("first@example.com"));
        User secondUser = users.save(newUser("second@example.com"));
        RefreshTokenSession active = refreshTokenSessions.save(
                new RefreshTokenSession(firstUser.getId(), hashWithLeadingByte(2),
                        NOW.plusSeconds(1_200), NOW));
        RefreshTokenSession revoked = refreshTokenSessions.save(
                new RefreshTokenSession(firstUser.getId(), hashWithLeadingByte(3),
                        NOW.plusSeconds(1_200), NOW));
        refreshTokenSessions.save(new RefreshTokenSession(secondUser.getId(), hashWithLeadingByte(4),
                NOW.plusSeconds(1_200), NOW));
        entityManager.flush();
        jdbc.update("UPDATE refresh_token_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?",
                revoked.getId());
        entityManager.clear();

        assertThat(refreshTokenSessions.findAllByUserIdAndRevokedAtIsNull(firstUser.getId()))
                .extracting(RefreshTokenSession::getId)
                .containsExactly(active.getId());
    }

    private User newUser(String email) {
        return new User(email, "password-hash", "Resident", null, null, UserStatus.ACTIVE,
                Set.of(Role.RESIDENT), NOW, NOW);
    }

    private byte[] hashWithLeadingByte(int value) {
        byte[] hash = new byte[32];
        hash[0] = (byte) value;
        return hash;
    }
}

package com.dormfix.identity.application;

import com.dormfix.identity.domain.RefreshTokenSession;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private static final String RAW_TOKEN = "refresh-token-under-test";

    @Mock
    private RefreshTokenSessionRepository sessions;
    @Mock
    private UserRepository users;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(sessions, users, accessTokenIssuer,
                new java.security.SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueStoresOnlySha256HashAndExpiresAfterFourteenDays() {
        ArgumentCaptor<RefreshTokenSession> captor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        when(sessions.save(any(RefreshTokenSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenIssue result = service.issue(42L, NOW);

        verify(sessions).save(captor.capture());
        RefreshTokenSession saved = captor.getValue();
        assertThat(result.rawToken()).isNotBlank().isNotEqualTo(RAW_TOKEN);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(14 * 24 * 60 * 60));
        assertThat(saved.getTokenHash()).hasSize(32)
                .isEqualTo(sha256(result.rawToken()));
        assertThat(new String(saved.getTokenHash(), StandardCharsets.UTF_8)).doesNotContain(result.rawToken());
    }

    @Test
    void rotationRevokesOldSessionBeforeIssuingReplacement() {
        RefreshTokenSession current = activeSession();
        User user = activeUser();
        when(sessions.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.of(current));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(accessTokenIssuer.issue(42L, Set.of(Role.RESIDENT), NOW))
                .thenReturn(new IssuedAccessToken("access-token", NOW.plusSeconds(1_800)));
        when(sessions.save(any(RefreshTokenSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenPairResult result = service.rotate(RAW_TOKEN);

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(RAW_TOKEN);
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(14 * 24 * 60 * 60));
        verify(sessions).save(current);
        verify(accessTokenIssuer).issue(42L, Set.of(Role.RESIDENT), NOW);
    }

    @Test
    void revokedExpiredAndInactiveSessionsAreRejectedWithoutIssuingAccessToken() {
        RefreshTokenSession revoked = activeSession();
        revoked.revoke(NOW.minusSeconds(1));
        when(sessions.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.rotate(RAW_TOKEN))
                .isInstanceOf(RefreshTokenRejectedException.class);
        verify(accessTokenIssuer, never()).issue(any(), any(), any());

        RefreshTokenSession expired = new RefreshTokenSession(42L, sha256(RAW_TOKEN),
                NOW.minusSeconds(1), NOW.minusSeconds(60));
        when(sessions.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.rotate(RAW_TOKEN))
                .isInstanceOf(RefreshTokenRejectedException.class);

        when(sessions.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.of(activeSession()));
        when(users.findById(42L)).thenReturn(Optional.of(activeUser(UserStatus.SUSPENDED)));
        assertThatThrownBy(() -> service.rotate(RAW_TOKEN))
                .isInstanceOf(RefreshTokenRejectedException.class);
    }

    @Test
    void revokeHashesTokenAndMakesTheSessionUnavailableForRotation() {
        RefreshTokenSession current = activeSession();
        when(sessions.findByTokenHash(sha256(RAW_TOKEN))).thenReturn(Optional.of(current));

        service.revoke(RAW_TOKEN);

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        verify(sessions).save(current);
    }

    private RefreshTokenSession activeSession() {
        return new RefreshTokenSession(42L, sha256(RAW_TOKEN), NOW.plusSeconds(1_200), NOW);
    }

    private User activeUser() {
        return activeUser(UserStatus.ACTIVE);
    }

    private User activeUser(UserStatus status) {
        User user = new User("resident@example.com", "encoded", "Resident", null, null,
                status, Set.of(Role.RESIDENT), NOW, NOW);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

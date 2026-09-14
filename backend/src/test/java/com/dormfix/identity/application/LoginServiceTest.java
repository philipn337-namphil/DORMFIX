package com.dormfix.identity.application;

import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;
    @Mock
    private RefreshTokenService refreshTokenService;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(users, passwordEncoder, accessTokenIssuer, refreshTokenService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authenticatesNormalizedActiveUserUpdatesLoginTimeAndIssuesAccessToken() {
        User user = user(UserStatus.ACTIVE);
        when(users.findByEmail("resident@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-secret", "encoded-secret")).thenReturn(true);
        when(accessTokenIssuer.issue(42L, Set.of(Role.RESIDENT), NOW))
                .thenReturn(new IssuedAccessToken("signed-jwt", NOW.plusSeconds(1_800)));
        when(refreshTokenService.issue(42L, NOW))
                .thenReturn(new RefreshTokenIssue("opaque-refresh-token", NOW.plus(14, java.time.temporal.ChronoUnit.DAYS)));

        LoginResult result = loginService.login(
                new LoginCommand(" Resident@Example.COM ", "plain-secret"));

        assertThat(result.accessToken()).isEqualTo("signed-jwt");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(1_800));
        assertThat(result.refreshToken()).isEqualTo("opaque-refresh-token");
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plus(14, java.time.temporal.ChronoUnit.DAYS));
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        verify(users).findByEmail("resident@example.com");
        verify(accessTokenIssuer).issue(42L, Set.of(Role.RESIDENT), NOW);
        verify(refreshTokenService).issue(42L, NOW);
    }

    @Test
    void rejectsUnknownEmailWithoutVerifyingPasswordOrIssuingToken() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(
                new LoginCommand("missing@example.com", "plain-secret")))
                .isInstanceOf(LoginRejectedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(accessTokenIssuer, never()).issue(any(), any(), any());
    }

    @Test
    void rejectsWrongPasswordWithoutUpdatingLoginTimeOrIssuingToken() {
        User user = user(UserStatus.ACTIVE);
        when(users.findByEmail("resident@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-secret", "encoded-secret")).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(
                new LoginCommand("resident@example.com", "wrong-secret")))
                .isInstanceOf(LoginRejectedException.class);

        assertThat(user.getLastLoginAt()).isNull();
        verify(accessTokenIssuer, never()).issue(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void rejectsInactiveUserWithoutUpdatingLoginTimeOrIssuingToken(UserStatus status) {
        User user = user(status);
        when(users.findByEmail("resident@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-secret", "encoded-secret")).thenReturn(true);

        assertThatThrownBy(() -> loginService.login(
                new LoginCommand("resident@example.com", "plain-secret")))
                .isInstanceOf(LoginRejectedException.class);

        assertThat(user.getLastLoginAt()).isNull();
        verify(accessTokenIssuer, never()).issue(any(), any(), any());
    }

    private User user(UserStatus status) {
        User user = new User("resident@example.com", "encoded-secret", "Resident", null, null,
                status, Set.of(Role.RESIDENT), NOW.minusSeconds(60), NOW.minusSeconds(60));
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }
}

package com.dormfix.identity.application;

import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public LoginService(UserRepository users, PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer, RefreshTokenService refreshTokenService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        String normalizedEmail = command.email().strip().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(normalizedEmail).orElseThrow(LoginRejectedException::new);
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new LoginRejectedException();
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        user.recordSuccessfulLogin(now);
        IssuedAccessToken token = accessTokenIssuer.issue(user.getId(), user.getRoles(), now);
        RefreshTokenIssue refreshToken = refreshTokenService.issue(user.getId(), now);
        return new LoginResult(token.value(), "Bearer", token.expiresAt(),
                refreshToken.rawToken(), refreshToken.expiresAt());
    }
}

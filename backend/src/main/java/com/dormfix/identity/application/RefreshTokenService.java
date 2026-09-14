package com.dormfix.identity.application;

import com.dormfix.identity.domain.RefreshTokenSession;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(14);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenSessionRepository sessions;
    private final UserRepository users;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureRandom random;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenSessionRepository sessions, UserRepository users,
            AccessTokenIssuer accessTokenIssuer, SecureRandom random, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.accessTokenIssuer = accessTokenIssuer;
        this.random = random;
        this.clock = clock;
    }

    public RefreshTokenIssue issue(Long userId, Instant issuedAt) {
        String rawToken = newRawToken();
        Instant expiresAt = issuedAt.plus(REFRESH_TOKEN_LIFETIME);
        sessions.save(new RefreshTokenSession(userId, sha256(rawToken), expiresAt, issuedAt));
        return new RefreshTokenIssue(rawToken, expiresAt);
    }

    @Transactional
    public TokenPairResult rotate(String rawToken) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        RefreshTokenSession session = sessions.findByTokenHash(sha256(rawToken))
                .orElseThrow(RefreshTokenRejectedException::new);
        if (session.getRevokedAt() != null || !now.isBefore(session.getExpiresAt())) {
            throw new RefreshTokenRejectedException();
        }

        User user = users.findById(session.getUserId()).orElseThrow(RefreshTokenRejectedException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RefreshTokenRejectedException();
        }

        session.revoke(now);
        sessions.save(session);
        IssuedAccessToken accessToken = accessTokenIssuer.issue(user.getId(), user.getRoles(), now);
        RefreshTokenIssue replacement = issue(user.getId(), now);
        return new TokenPairResult(accessToken.value(), "Bearer", accessToken.expiresAt(),
                replacement.rawToken(), replacement.expiresAt());
    }

    @Transactional
    public void revoke(String rawToken) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        RefreshTokenSession session = sessions.findByTokenHash(sha256(rawToken))
                .orElseThrow(RefreshTokenRejectedException::new);
        session.revoke(now);
        sessions.save(session);
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(String rawToken) {
        Objects.requireNonNull(rawToken);
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }
}

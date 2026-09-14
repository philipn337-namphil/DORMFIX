package com.dormfix.identity.application;

import java.time.Instant;

public record TokenPairResult(String accessToken, String tokenType, Instant expiresAt,
        String refreshToken, Instant refreshTokenExpiresAt) {
}

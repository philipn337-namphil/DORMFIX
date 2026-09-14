package com.dormfix.identity.api;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt,
        String refreshToken, Instant refreshTokenExpiresAt) {
}

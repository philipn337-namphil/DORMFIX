package com.dormfix.identity.api;

import com.dormfix.identity.application.TokenPairResult;
import java.time.Instant;

public record TokenPairResponse(String accessToken, String tokenType, Instant expiresAt,
        String refreshToken, Instant refreshTokenExpiresAt) {
    public static TokenPairResponse from(TokenPairResult result) {
        return new TokenPairResponse(result.accessToken(), result.tokenType(), result.expiresAt(),
                result.refreshToken(), result.refreshTokenExpiresAt());
    }
}

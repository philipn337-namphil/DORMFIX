package com.dormfix.identity.application;

import java.time.Instant;

public record RefreshTokenIssue(String rawToken, Instant expiresAt) {
}

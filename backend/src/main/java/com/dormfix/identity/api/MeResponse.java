package com.dormfix.identity.api;

import com.dormfix.identity.application.CurrentUserResult;
import java.time.Instant;
import java.util.Set;

public record MeResponse(Long id, String email, String name, String phone,
        String studentNumber, String status, Set<String> roles, Instant lastLoginAt,
        Instant createdAt, Instant updatedAt) {
    public static MeResponse from(CurrentUserResult result) {
        return new MeResponse(result.id(), result.email(), result.name(), result.phone(),
                result.studentNumber(), result.status(), result.roles(), result.lastLoginAt(),
                result.createdAt(), result.updatedAt());
    }
}

package com.dormfix.identity.application;

import java.time.Instant;
import java.util.Set;

public record CurrentUserResult(Long id, String email, String name, String phone,
        String studentNumber, String status, Set<String> roles, Instant lastLoginAt,
        Instant createdAt, Instant updatedAt) {
}

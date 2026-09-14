package com.dormfix.identity.api;

import java.time.Instant;
import java.util.Set;

public record SignupResponse(Long id, String email, String name, String phone,
        String studentNumber, String status, Set<String> roles, Instant createdAt) {
}

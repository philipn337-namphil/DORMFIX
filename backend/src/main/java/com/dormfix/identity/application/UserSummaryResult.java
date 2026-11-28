package com.dormfix.identity.application;

import java.util.List;

/** Deliberately minimal: no phone, student number, login time or timestamps (ADR-030). */
public record UserSummaryResult(Long id, String name, String email, List<String> roles,
        String status) { }

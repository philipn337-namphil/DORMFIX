package com.dormfix.identity.application;

import com.dormfix.identity.domain.Role;
import java.time.Instant;
import java.util.Set;

public interface AccessTokenIssuer {
    IssuedAccessToken issue(Long userId, Set<Role> roles, Instant issuedAt);
}

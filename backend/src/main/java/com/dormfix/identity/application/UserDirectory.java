package com.dormfix.identity.application;

import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.util.List;
import java.util.Optional;

/**
 * Administrator user lookups (ADR-030). The resident path for an ADMIN is a separate exact-email
 * method, so a resident listing cannot be reached through a filter argument.
 */
public interface UserDirectory {
    /** Null filters are absent. Ordered name ASC, id ASC. */
    List<User> search(Role role, UserStatus status, String email, int page, int size);

    long count(Role role, UserStatus status, String email);

    /** The user with exactly this normalized email, only if they hold RESIDENT. */
    Optional<User> findResidentByEmail(String email);
}

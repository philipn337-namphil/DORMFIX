package com.dormfix.identity.application;

import com.dormfix.identity.domain.Role;
import java.util.Set;

/**
 * ADR-030: which user lookup a caller may run. Workers carry no dormitory scope, so an ADMIN sees
 * all of them; residents are reachable by an ADMIN only through one exact email, so no ADMIN can
 * enumerate another dormitory's residents. Everything else is denied.
 */
public final class AdminUserAccessPolicy {
    public enum Lookup { ALL_USERS, WORKERS, RESIDENT_BY_EMAIL }

    private AdminUserAccessPolicy() { }

    /** {@code email} is already normalized; null means absent. */
    public static Lookup decide(Set<Role> caller, Role roleFilter, String email) {
        if (caller.contains(Role.SUPER_ADMIN)) {
            return Lookup.ALL_USERS;
        }
        if (caller.contains(Role.ADMIN)) {
            if (roleFilter == Role.WORKER) {
                return Lookup.WORKERS;
            }
            if (roleFilter == Role.RESIDENT && email != null) {
                return Lookup.RESIDENT_BY_EMAIL;
            }
        }
        throw new DormitoryScopeAccessDeniedException();
    }
}

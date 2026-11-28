package com.dormfix.maintenance.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryNotFoundException;
import java.util.List;
import java.util.Set;

/**
 * ADR-030 dashboard scope. An ADMIN naming a dormitory outside its scope is denied whether or not
 * that dormitory exists, so the answer never tells an ADMIN which ids exist elsewhere.
 */
final class DashboardScope {
    private DashboardScope() { }

    /** Returns the dormitory ids to count, ascending. */
    static List<Long> resolve(Set<Role> caller, Set<Long> adminScope, Long requested,
            Set<Long> existing) {
        if (caller.contains(Role.SUPER_ADMIN)) {
            if (requested == null) {
                return existing.stream().sorted().toList();
            }
            if (!existing.contains(requested)) {
                throw new DormitoryNotFoundException(requested);
            }
            return List.of(requested);
        }
        if (caller.contains(Role.ADMIN) && !adminScope.isEmpty()) {
            if (requested == null) {
                return adminScope.stream().sorted().toList();
            }
            if (adminScope.contains(requested)) {
                return List.of(requested);
            }
        }
        throw new DormitoryScopeAccessDeniedException();
    }
}

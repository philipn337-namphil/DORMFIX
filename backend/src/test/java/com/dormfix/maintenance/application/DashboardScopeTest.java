package com.dormfix.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryNotFoundException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ADR-030 dashboard scope rules. Existing dormitories are 1, 2, 3; the admin holds 3 and 1. */
class DashboardScopeTest {
    private static final Set<Long> EXISTING = Set.of(1L, 2L, 3L);
    private static final Set<Long> ADMIN_SCOPE = Set.of(3L, 1L);

    @Test
    void superAdminCountsEverythingOrOneExistingDormitory() {
        assertThat(DashboardScope.resolve(Set.of(Role.SUPER_ADMIN), Set.of(), null, EXISTING))
                .containsExactly(1L, 2L, 3L);
        assertThat(DashboardScope.resolve(Set.of(Role.SUPER_ADMIN), Set.of(), 2L, EXISTING))
                .containsExactly(2L);
        assertThatThrownBy(() -> DashboardScope.resolve(Set.of(Role.SUPER_ADMIN), Set.of(), 9L,
                EXISTING)).isInstanceOf(DormitoryNotFoundException.class);
    }

    @Test
    void adminCountsTheScopeOrOneScopedDormitory() {
        assertThat(DashboardScope.resolve(Set.of(Role.ADMIN), ADMIN_SCOPE, null, EXISTING))
                .containsExactly(1L, 3L);
        assertThat(DashboardScope.resolve(Set.of(Role.ADMIN), ADMIN_SCOPE, 3L, EXISTING))
                .containsExactly(3L);
    }

    @Test
    void adminOutsideScopeOrWithoutScopeIsDeniedEvenForAMissingDormitory() {
        assertThatThrownBy(() -> DashboardScope.resolve(Set.of(Role.ADMIN), ADMIN_SCOPE, 2L,
                EXISTING)).isInstanceOf(DormitoryScopeAccessDeniedException.class);
        // 403, not 404: an admin must not learn which dormitory ids exist outside the scope.
        assertThatThrownBy(() -> DashboardScope.resolve(Set.of(Role.ADMIN), ADMIN_SCOPE, 9L,
                EXISTING)).isInstanceOf(DormitoryScopeAccessDeniedException.class);
        assertThatThrownBy(() -> DashboardScope.resolve(Set.of(Role.ADMIN), Set.of(), null,
                EXISTING)).isInstanceOf(DormitoryScopeAccessDeniedException.class);
    }

    @Test
    void nonAdministratorsAreDenied() {
        for (Role role : new Role[] {Role.RESIDENT, Role.WORKER}) {
            assertThatThrownBy(() -> DashboardScope.resolve(Set.of(role), Set.of(), null,
                    EXISTING)).isInstanceOf(DormitoryScopeAccessDeniedException.class);
        }
    }
}

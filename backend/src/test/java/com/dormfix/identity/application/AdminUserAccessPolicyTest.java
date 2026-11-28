package com.dormfix.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.identity.application.AdminUserAccessPolicy.Lookup;
import com.dormfix.identity.domain.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ADR-030 GET /admin/users permission table. */
class AdminUserAccessPolicyTest {
    private static final Set<Role> SUPER = Set.of(Role.SUPER_ADMIN);
    private static final Set<Role> ADMIN = Set.of(Role.ADMIN);

    @Test
    void superAdminListsEveryoneWithAnyFilter() {
        assertThat(AdminUserAccessPolicy.decide(SUPER, null, null)).isEqualTo(Lookup.ALL_USERS);
        assertThat(AdminUserAccessPolicy.decide(SUPER, Role.ADMIN, null)).isEqualTo(Lookup.ALL_USERS);
        assertThat(AdminUserAccessPolicy.decide(SUPER, Role.RESIDENT, null))
                .isEqualTo(Lookup.ALL_USERS);
    }

    @Test
    void adminListsWorkersAndLooksUpOneResidentByExactEmail() {
        assertThat(AdminUserAccessPolicy.decide(ADMIN, Role.WORKER, null)).isEqualTo(Lookup.WORKERS);
        assertThat(AdminUserAccessPolicy.decide(ADMIN, Role.RESIDENT, "a@b.c"))
                .isEqualTo(Lookup.RESIDENT_BY_EMAIL);
    }

    @Test
    void adminCannotBrowseResidentsOrAdministrators() {
        for (Role role : new Role[] {null, Role.ADMIN, Role.SUPER_ADMIN}) {
            assertThatThrownBy(() -> AdminUserAccessPolicy.decide(ADMIN, role, null))
                    .isInstanceOf(DormitoryScopeAccessDeniedException.class);
        }
        assertThatThrownBy(() -> AdminUserAccessPolicy.decide(ADMIN, Role.RESIDENT, null))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
        assertThatThrownBy(() -> AdminUserAccessPolicy.decide(ADMIN, Role.ADMIN, "a@b.c"))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
    }

    @Test
    void callersWithoutAnAdministratorRoleAreDenied() {
        for (Set<Role> caller : java.util.List.of(Set.of(Role.RESIDENT), Set.of(Role.WORKER),
                Set.of(Role.RESIDENT, Role.WORKER), Set.<Role>of())) {
            assertThatThrownBy(() -> AdminUserAccessPolicy.decide(caller, Role.WORKER, null))
                    .isInstanceOf(DormitoryScopeAccessDeniedException.class);
        }
    }

    @Test
    void aCallerHoldingBothAdministratorRolesGetsTheSuperAdminView() {
        assertThat(AdminUserAccessPolicy.decide(Set.of(Role.ADMIN, Role.SUPER_ADMIN), null, null))
                .isEqualTo(Lookup.ALL_USERS);
    }
}

package com.dormfix.identity.application;

import com.dormfix.identity.domain.AdminDormitoryScopeId;
import com.dormfix.identity.domain.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DormitoryScopeAuthorizationServiceTest {
    private static final Long USER_ID = 10L;
    private static final Long DORMITORY_ID = 20L;

    @Mock
    private AdminDormitoryScopeRepository scopes;
    @InjectMocks
    private DormitoryScopeAuthorizationService service;

    @Test
    void superAdminIsAllowedWithoutScopeLookup() {
        assertThatCode(() -> service.requireManageAccess(USER_ID, Set.of(Role.SUPER_ADMIN), DORMITORY_ID))
                .doesNotThrowAnyException();

        verify(scopes, never()).existsById(new AdminDormitoryScopeId(USER_ID, DORMITORY_ID));
    }

    @Test
    void adminWithMatchingScopeIsAllowed() {
        when(scopes.existsById(new AdminDormitoryScopeId(USER_ID, DORMITORY_ID))).thenReturn(true);

        assertThatCode(() -> service.requireManageAccess(USER_ID, Set.of(Role.ADMIN), DORMITORY_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void adminWithoutMatchingScopeIsDenied() {
        when(scopes.existsById(new AdminDormitoryScopeId(USER_ID, DORMITORY_ID))).thenReturn(false);

        assertDenied(Set.of(Role.ADMIN));
    }

    @Test
    void residentAndWorkerAreDeniedEvenWhenScopeRowExists() {
        assertDenied(Set.of(Role.RESIDENT));
        assertDenied(Set.of(Role.WORKER));
        verify(scopes, never()).existsById(new AdminDormitoryScopeId(USER_ID, DORMITORY_ID));
    }

    @Test
    void mixedAdminResidentRolesStillRequireAdminScope() {
        when(scopes.existsById(new AdminDormitoryScopeId(USER_ID, DORMITORY_ID))).thenReturn(true);

        assertThatCode(() -> service.requireManageAccess(
                USER_ID, Set.of(Role.ADMIN, Role.RESIDENT), DORMITORY_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidAuthorizationInputsAreDenied() {
        assertDenied(null, Set.of(Role.SUPER_ADMIN), DORMITORY_ID);
        assertDenied(USER_ID, Set.of(Role.ADMIN), null);
        assertDenied(USER_ID, null, DORMITORY_ID);
    }

    private void assertDenied(Set<Role> roles) {
        assertDenied(USER_ID, roles, DORMITORY_ID);
    }

    private void assertDenied(Long userId, Set<Role> roles, Long dormitoryId) {
        assertThatThrownBy(() -> service.requireManageAccess(userId, roles, dormitoryId))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class)
                .hasMessage("Access is denied.");
    }
}

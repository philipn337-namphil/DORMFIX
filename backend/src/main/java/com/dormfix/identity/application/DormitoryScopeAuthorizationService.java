package com.dormfix.identity.application;

import com.dormfix.identity.domain.AdminDormitoryScopeId;
import com.dormfix.identity.domain.Role;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DormitoryScopeAuthorizationService {
    private final AdminDormitoryScopeRepository scopes;

    public DormitoryScopeAuthorizationService(AdminDormitoryScopeRepository scopes) {
        this.scopes = scopes;
    }

    @Transactional(readOnly = true)
    public void requireManageAccess(Long userId, Set<Role> roles, Long dormitoryId) {
        if (userId == null || dormitoryId == null || roles == null) {
            throw new DormitoryScopeAccessDeniedException();
        }
        if (roles.contains(Role.SUPER_ADMIN)) {
            return;
        }
        if (!roles.contains(Role.ADMIN)
                || !scopes.existsById(new AdminDormitoryScopeId(userId, dormitoryId))) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }
}

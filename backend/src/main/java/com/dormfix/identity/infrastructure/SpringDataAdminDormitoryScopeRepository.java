package com.dormfix.identity.infrastructure;

import com.dormfix.identity.domain.AdminDormitoryScope;
import com.dormfix.identity.domain.AdminDormitoryScopeId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAdminDormitoryScopeRepository
        extends JpaRepository<AdminDormitoryScope, AdminDormitoryScopeId> {
    List<AdminDormitoryScope> findAllByIdUserIdOrderByIdDormitoryIdAsc(Long userId);
}

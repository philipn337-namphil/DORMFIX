package com.dormfix.identity.application;

import com.dormfix.identity.domain.AdminDormitoryScope;
import com.dormfix.identity.domain.AdminDormitoryScopeId;
import java.util.List;

public interface AdminDormitoryScopeRepository {
    AdminDormitoryScope save(AdminDormitoryScope scope);

    boolean existsById(AdminDormitoryScopeId id);

    List<AdminDormitoryScope> findAllByUserId(Long userId);

    void deleteById(AdminDormitoryScopeId id);
}

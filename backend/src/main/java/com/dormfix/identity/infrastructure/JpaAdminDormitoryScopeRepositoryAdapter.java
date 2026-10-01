package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.domain.AdminDormitoryScope;
import com.dormfix.identity.domain.AdminDormitoryScopeId;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class JpaAdminDormitoryScopeRepositoryAdapter implements AdminDormitoryScopeRepository {
    private final SpringDataAdminDormitoryScopeRepository delegate;

    JpaAdminDormitoryScopeRepositoryAdapter(SpringDataAdminDormitoryScopeRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public AdminDormitoryScope save(AdminDormitoryScope scope) {
        return delegate.save(scope);
    }

    @Override
    public boolean existsById(AdminDormitoryScopeId id) {
        return delegate.existsById(id);
    }

    @Override
    public List<AdminDormitoryScope> findAllByUserId(Long userId) {
        return delegate.findAllByIdUserIdOrderByIdDormitoryIdAsc(userId);
    }

    @Override
    public void deleteById(AdminDormitoryScopeId id) {
        delegate.deleteById(id);
    }
}

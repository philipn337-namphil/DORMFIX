package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.UserDirectory;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserDirectoryAdapter implements UserDirectory {
    private final SpringDataUserRepository delegate;

    JpaUserDirectoryAdapter(SpringDataUserRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<User> search(Role role, UserStatus status, String email, int page, int size) {
        return delegate.search(role, status, email, PageRequest.of(page, size));
    }

    @Override
    public long count(Role role, UserStatus status, String email) {
        return delegate.countSearch(role, status, email);
    }

    @Override
    public Optional<User> findResidentByEmail(String email) {
        return delegate.findByEmail(email).filter(user -> user.getRoles().contains(Role.RESIDENT));
    }
}

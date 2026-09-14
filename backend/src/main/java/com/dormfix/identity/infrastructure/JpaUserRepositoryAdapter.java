package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRepositoryAdapter implements UserRepository {
    private final SpringDataUserRepository delegate;

    JpaUserRepositoryAdapter(SpringDataUserRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public User save(User user) {
        return delegate.save(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return delegate.findByEmail(email);
    }

    @Override
    public Optional<User> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return delegate.existsByEmail(email);
    }
}

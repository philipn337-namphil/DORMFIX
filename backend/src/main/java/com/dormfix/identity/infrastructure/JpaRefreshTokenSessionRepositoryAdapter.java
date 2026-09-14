package com.dormfix.identity.infrastructure;

import com.dormfix.identity.application.RefreshTokenSessionRepository;
import com.dormfix.identity.domain.RefreshTokenSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaRefreshTokenSessionRepositoryAdapter implements RefreshTokenSessionRepository {
    private final SpringDataRefreshTokenSessionRepository delegate;

    JpaRefreshTokenSessionRepositoryAdapter(SpringDataRefreshTokenSessionRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public RefreshTokenSession save(RefreshTokenSession session) {
        return delegate.save(session);
    }

    @Override
    public Optional<RefreshTokenSession> findByTokenHash(byte[] tokenHash) {
        return delegate.findByTokenHash(tokenHash);
    }

    @Override
    public List<RefreshTokenSession> findAllByUserIdAndRevokedAtIsNull(Long userId) {
        return delegate.findAllByUserIdAndRevokedAtIsNull(userId);
    }
}

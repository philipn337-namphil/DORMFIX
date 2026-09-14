package com.dormfix.identity.application;

import com.dormfix.identity.domain.RefreshTokenSession;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenSessionRepository {
    RefreshTokenSession save(RefreshTokenSession session);

    Optional<RefreshTokenSession> findByTokenHash(byte[] tokenHash);

    List<RefreshTokenSession> findAllByUserIdAndRevokedAtIsNull(Long userId);
}

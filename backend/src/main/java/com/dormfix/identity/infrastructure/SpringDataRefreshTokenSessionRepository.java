package com.dormfix.identity.infrastructure;

import com.dormfix.identity.domain.RefreshTokenSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {
    Optional<RefreshTokenSession> findByTokenHash(byte[] tokenHash);

    List<RefreshTokenSession> findAllByUserIdAndRevokedAtIsNull(Long userId);
}

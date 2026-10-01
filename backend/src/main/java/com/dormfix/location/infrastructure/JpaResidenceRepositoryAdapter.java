package com.dormfix.location.infrastructure;
import com.dormfix.location.application.ResidenceRepository;
import com.dormfix.location.domain.Residence;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
@Repository class JpaResidenceRepositoryAdapter implements ResidenceRepository {
    private final SpringDataResidenceRepository delegate;
    JpaResidenceRepositoryAdapter(SpringDataResidenceRepository delegate) { this.delegate = delegate; }
    public Residence save(Residence value) { return delegate.save(value); }
    public Optional<Residence> findById(Long id) { return delegate.findById(id); }
    public List<Residence> findAllByResidentIdOrderByStartDateDescIdDesc(Long id) { return delegate.findAllByResidentIdOrderByStartDateDescIdDesc(id); }
    public List<Residence> findAllByRoomSpaceIdOrderByStartDateDescIdDesc(Long id) { return delegate.findAllByRoomSpaceIdOrderByStartDateDescIdDesc(id); }
    public List<Residence> findAllByOrderByStartDateDescIdDesc() { return delegate.findAllByOrderByStartDateDescIdDesc(); }
    public Page<Residence> findPage(Long residentId, Long roomSpaceId, Boolean current, Set<Long> scopeIds, boolean allScopes, Pageable pageable) { return delegate.findPage(residentId, roomSpaceId, current, scopeIds, allScopes, pageable); }
    public Page<Residence> findOwnPage(Long residentId, Boolean current, Pageable pageable) { return delegate.findOwnPage(residentId, current, pageable); }
    public boolean existsOverlapping(Long id, LocalDate start, LocalDate end) { return delegate.existsOverlapping(id, start, end); }
}

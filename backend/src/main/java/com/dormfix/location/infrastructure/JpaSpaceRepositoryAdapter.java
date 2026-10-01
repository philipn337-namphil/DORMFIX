package com.dormfix.location.infrastructure;

import com.dormfix.location.application.SpaceRepository;
import com.dormfix.location.domain.Space;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaSpaceRepositoryAdapter implements SpaceRepository {
    private final SpringDataSpaceRepository delegate;

    JpaSpaceRepositoryAdapter(SpringDataSpaceRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Space save(Space space) {
        return delegate.save(space);
    }

    @Override
    public Optional<Space> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return delegate.existsById(id);
    }

    @Override
    public List<Space> findAllByBuildingIdAndActiveTrueOrderByCodeAsc(Long buildingId) {
        return delegate.findAllByBuildingIdAndActiveTrueOrderByCodeAsc(buildingId);
    }
}

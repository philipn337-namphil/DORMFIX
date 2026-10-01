package com.dormfix.catalog.infrastructure;

import com.dormfix.catalog.application.FacilityRepository;
import com.dormfix.catalog.domain.Facility;
import com.dormfix.location.application.FacilityScopeLookup;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaFacilityRepositoryAdapter implements FacilityRepository, FacilityScopeLookup {
    private final SpringDataFacilityRepository delegate;

    JpaFacilityRepositoryAdapter(SpringDataFacilityRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Facility save(Facility facility) {
        return delegate.save(facility);
    }

    @Override
    public Optional<Facility> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Facility> findAllBySpaceIdOrderByNameAsc(Long spaceId) {
        return delegate.findAllBySpaceIdOrderByNameAsc(spaceId);
    }

    @Override
    public java.util.Optional<Long> findSpaceIdByFacilityId(Long facilityId) {
        return delegate.findById(facilityId).map(Facility::getSpaceId);
    }
}

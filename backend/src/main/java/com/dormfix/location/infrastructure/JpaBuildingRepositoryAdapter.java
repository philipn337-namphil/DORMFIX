package com.dormfix.location.infrastructure;

import com.dormfix.location.application.BuildingRepository;
import com.dormfix.location.domain.Building;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaBuildingRepositoryAdapter implements BuildingRepository {
    private final SpringDataBuildingRepository delegate;

    JpaBuildingRepositoryAdapter(SpringDataBuildingRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Building save(Building building) {
        return delegate.save(building);
    }

    @Override
    public Optional<Building> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Building> findAllByDormitoryIdAndActiveTrueOrderByCodeAsc(Long dormitoryId) {
        return delegate.findAllByDormitoryIdAndActiveTrueOrderByCodeAsc(dormitoryId);
    }
}

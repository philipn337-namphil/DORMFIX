package com.dormfix.catalog.infrastructure;

import com.dormfix.catalog.application.MaintenanceCategoryRepository;
import com.dormfix.catalog.domain.MaintenanceCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaMaintenanceCategoryRepositoryAdapter implements MaintenanceCategoryRepository {
    private final SpringDataMaintenanceCategoryRepository delegate;

    JpaMaintenanceCategoryRepositoryAdapter(SpringDataMaintenanceCategoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public MaintenanceCategory save(MaintenanceCategory category) {
        return delegate.save(category);
    }

    @Override
    public Optional<MaintenanceCategory> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<MaintenanceCategory> findAllByActiveTrueOrderBySortOrderAscCodeAsc() {
        return delegate.findAllByActiveTrueOrderBySortOrderAscCodeAsc();
    }
}

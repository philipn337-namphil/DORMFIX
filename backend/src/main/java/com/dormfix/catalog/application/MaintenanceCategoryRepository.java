package com.dormfix.catalog.application;

import com.dormfix.catalog.domain.MaintenanceCategory;
import java.util.List;
import java.util.Optional;

public interface MaintenanceCategoryRepository {
    MaintenanceCategory save(MaintenanceCategory category);

    Optional<MaintenanceCategory> findById(Long id);

    List<MaintenanceCategory> findAllByActiveTrueOrderBySortOrderAscCodeAsc();
}

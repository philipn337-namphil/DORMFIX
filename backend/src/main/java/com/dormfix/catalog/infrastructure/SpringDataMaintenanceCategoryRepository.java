package com.dormfix.catalog.infrastructure;

import com.dormfix.catalog.domain.MaintenanceCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMaintenanceCategoryRepository extends JpaRepository<MaintenanceCategory, Long> {
    List<MaintenanceCategory> findAllByActiveTrueOrderBySortOrderAscCodeAsc();
}

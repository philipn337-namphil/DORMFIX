package com.dormfix.location.infrastructure;

import com.dormfix.location.domain.Building;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBuildingRepository extends JpaRepository<Building, Long> {
    Optional<Building> findById(Long id);

    List<Building> findAllByDormitoryIdAndActiveTrueOrderByCodeAsc(Long dormitoryId);
}

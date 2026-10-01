package com.dormfix.location.infrastructure;

import com.dormfix.location.domain.Space;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSpaceRepository extends JpaRepository<Space, Long> {
    List<Space> findAllByBuildingIdAndActiveTrueOrderByCodeAsc(Long buildingId);
}

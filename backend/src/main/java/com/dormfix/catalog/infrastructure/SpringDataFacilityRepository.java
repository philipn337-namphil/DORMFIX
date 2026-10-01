package com.dormfix.catalog.infrastructure;

import com.dormfix.catalog.domain.Facility;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFacilityRepository extends JpaRepository<Facility, Long> {
    List<Facility> findAllBySpaceIdOrderByNameAsc(Long spaceId);
}

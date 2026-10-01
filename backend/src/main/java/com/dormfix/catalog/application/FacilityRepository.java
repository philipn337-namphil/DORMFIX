package com.dormfix.catalog.application;

import com.dormfix.catalog.domain.Facility;
import java.util.List;
import java.util.Optional;

public interface FacilityRepository {
    Facility save(Facility facility);

    Optional<Facility> findById(Long id);

    List<Facility> findAllBySpaceIdOrderByNameAsc(Long spaceId);
}

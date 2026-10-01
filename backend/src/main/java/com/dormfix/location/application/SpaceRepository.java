package com.dormfix.location.application;

import com.dormfix.location.domain.Space;
import java.util.List;
import java.util.Optional;

public interface SpaceRepository {
    Space save(Space space);

    Optional<Space> findById(Long id);

    boolean existsById(Long id);

    List<Space> findAllByBuildingIdAndActiveTrueOrderByCodeAsc(Long buildingId);
}

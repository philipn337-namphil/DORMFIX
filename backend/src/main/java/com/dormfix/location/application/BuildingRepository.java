package com.dormfix.location.application;

import com.dormfix.location.domain.Building;
import java.util.List;
import java.util.Optional;

public interface BuildingRepository {
    Building save(Building building);

    Optional<Building> findById(Long id);

    List<Building> findAllByDormitoryIdAndActiveTrueOrderByCodeAsc(Long dormitoryId);
}

package com.dormfix.location.application;

import com.dormfix.location.domain.Dormitory;
import java.util.List;
import java.util.Optional;

public interface DormitoryRepository {
    Dormitory save(Dormitory dormitory);

    Optional<Dormitory> findById(Long id);

    List<Dormitory> findAllByActiveTrueOrderByNameAsc();
}

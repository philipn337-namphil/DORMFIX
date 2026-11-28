package com.dormfix.location.application;

import com.dormfix.location.domain.Dormitory;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DormitoryRepository {
    Dormitory save(Dormitory dormitory);

    Optional<Dormitory> findById(Long id);

    List<Dormitory> findAllByActiveTrueOrderByNameAsc();

    /** Every dormitory id, inactive ones included. */
    Set<Long> findAllIds();
}

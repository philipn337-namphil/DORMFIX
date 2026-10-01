package com.dormfix.location.infrastructure;

import com.dormfix.location.domain.Dormitory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataDormitoryRepository extends JpaRepository<Dormitory, Long> {
    Optional<Dormitory> findById(Long id);

    List<Dormitory> findAllByActiveTrueOrderByNameAsc();
}

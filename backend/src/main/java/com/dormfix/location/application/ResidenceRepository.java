package com.dormfix.location.application;
import com.dormfix.location.domain.Residence;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface ResidenceRepository {
    Residence save(Residence residence); Optional<Residence> findById(Long id);
    List<Residence> findAllByResidentIdOrderByStartDateDescIdDesc(Long residentId);
    List<Residence> findAllByRoomSpaceIdOrderByStartDateDescIdDesc(Long roomSpaceId);
    List<Residence> findAllByOrderByStartDateDescIdDesc();
    Page<Residence> findPage(Long residentId, Long roomSpaceId, Boolean current, Set<Long> dormitoryIds, boolean allScopes, Pageable pageable);
    Page<Residence> findOwnPage(Long residentId, Boolean current, Pageable pageable);
    boolean existsOverlapping(Long residentId, LocalDate startDate, LocalDate endDate);
}

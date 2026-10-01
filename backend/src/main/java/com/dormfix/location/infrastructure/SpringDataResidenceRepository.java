package com.dormfix.location.infrastructure;

import com.dormfix.location.domain.Residence;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataResidenceRepository extends JpaRepository<Residence, Long> {
    String CURRENT = "CAST(CURRENT_TIMESTAMP AT TIME ZONE d.timezone AS date)";
    String CURRENT_FILTER = "(:current IS NULL OR (:current = true AND r.start_date <= " + CURRENT
            + " AND (r.end_date IS NULL OR " + CURRENT + " < r.end_date)) OR (:current = false AND NOT (r.start_date <= "
            + CURRENT + " AND (r.end_date IS NULL OR " + CURRENT + " < r.end_date))))";
    List<Residence> findAllByResidentIdOrderByStartDateDescIdDesc(Long residentId);
    List<Residence> findAllByRoomSpaceIdOrderByStartDateDescIdDesc(Long roomSpaceId);
    List<Residence> findAllByOrderByStartDateDescIdDesc();
    @Query(value="SELECT r.* FROM residence r JOIN space s ON s.id=r.room_space_id JOIN building b ON b.id=s.building_id JOIN dormitory d ON d.id=b.dormitory_id WHERE (:residentId IS NULL OR r.resident_id=:residentId) AND (:roomSpaceId IS NULL OR r.room_space_id=:roomSpaceId) AND " + CURRENT_FILTER + " AND (:allScopes=true OR b.dormitory_id IN (:dormitoryIds)) ORDER BY r.start_date DESC,r.id DESC", countQuery="SELECT count(*) FROM residence r JOIN space s ON s.id=r.room_space_id JOIN building b ON b.id=s.building_id JOIN dormitory d ON d.id=b.dormitory_id WHERE (:residentId IS NULL OR r.resident_id=:residentId) AND (:roomSpaceId IS NULL OR r.room_space_id=:roomSpaceId) AND " + CURRENT_FILTER + " AND (:allScopes=true OR b.dormitory_id IN (:dormitoryIds))", nativeQuery=true)
    Page<Residence> findPage(@Param("residentId") Long residentId,@Param("roomSpaceId") Long roomSpaceId,@Param("current") Boolean current,@Param("dormitoryIds") Set<Long> dormitoryIds,@Param("allScopes") boolean allScopes,Pageable pageable);
    @Query(value="SELECT r.* FROM residence r JOIN space s ON s.id=r.room_space_id JOIN building b ON b.id=s.building_id JOIN dormitory d ON d.id=b.dormitory_id WHERE r.resident_id=:residentId AND " + CURRENT_FILTER + " ORDER BY r.start_date DESC,r.id DESC", countQuery="SELECT count(*) FROM residence r JOIN space s ON s.id=r.room_space_id JOIN building b ON b.id=s.building_id JOIN dormitory d ON d.id=b.dormitory_id WHERE r.resident_id=:residentId AND " + CURRENT_FILTER, nativeQuery=true)
    Page<Residence> findOwnPage(@Param("residentId") Long residentId,@Param("current") Boolean current,Pageable pageable);
    @Query(value="SELECT EXISTS (SELECT 1 FROM residence WHERE resident_id=:residentId AND daterange(start_date,end_date,'[)') && daterange(:startDate,:endDate,'[)'))",nativeQuery=true)
    boolean existsOverlapping(Long residentId,LocalDate startDate,LocalDate endDate);
}

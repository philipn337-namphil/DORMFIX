package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.MaintenanceRequest;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {
    /**
     * Read visibility. A caller may hold several roles, so the branches are a union. The
     * dormitory list is never empty; callers pass a sentinel that cannot match a real id.
     */
    String VISIBLE = "(:allScopes = true"
            + " OR (:resident = true AND r.reporter_id = :actorId)"
            + " OR (:worker = true AND EXISTS (SELECT 1 FROM maintenance_assignment a"
            + " WHERE a.request_id = r.id AND a.worker_id = :actorId))"
            + " OR b.dormitory_id IN (:dormitoryIds))";

    String FILTERS = "(:statusCount = 0 OR r.status IN (:statuses))"
            + " AND (:priority IS NULL OR r.priority = :priority)"
            + " AND (:categoryId IS NULL OR r.category_id = :categoryId)"
            + " AND (:dormitoryId IS NULL OR b.dormitory_id = :dormitoryId)"
            + " AND (:buildingId IS NULL OR s.building_id = :buildingId)"
            + " AND (:spaceId IS NULL OR r.space_id = :spaceId)"
            + " AND (:assigneeId IS NULL OR EXISTS (SELECT 1 FROM maintenance_assignment aa"
            + " WHERE aa.request_id = r.id AND aa.worker_id = :assigneeId"
            + " AND aa.unassigned_at IS NULL))";

    String JOINS = " FROM maintenance_request r JOIN space s ON s.id = r.space_id"
            + " JOIN building b ON b.id = s.building_id";

    String WHERE = " WHERE " + VISIBLE + " AND " + FILTERS;

    // The ORDER BY is inside the native statement, so callers must pass an unsorted Pageable:
    // Spring Data would otherwise append Java property names to this SQL and break it.
    @Query(value = "SELECT r.*" + JOINS + WHERE + " ORDER BY r.created_at DESC, r.id DESC",
            countQuery = "SELECT count(*)" + JOINS + WHERE, nativeQuery = true)
    Page<MaintenanceRequest> findVisiblePage(@Param("actorId") Long actorId,
            @Param("allScopes") boolean allScopes, @Param("resident") boolean resident,
            @Param("worker") boolean worker, @Param("dormitoryIds") Set<Long> dormitoryIds,
            @Param("statuses") Set<String> statuses, @Param("statusCount") int statusCount,
            @Param("priority") String priority, @Param("categoryId") Long categoryId,
            @Param("dormitoryId") Long dormitoryId, @Param("buildingId") Long buildingId,
            @Param("spaceId") Long spaceId, @Param("assigneeId") Long assigneeId,
            Pageable pageable);

    String ASSIGNED_JOINS = " FROM maintenance_request r JOIN maintenance_assignment a"
            + " ON a.request_id = r.id";

    String ASSIGNED_WHERE = " WHERE a.worker_id = :workerId"
            + " AND ((:active = true AND a.unassigned_at IS NULL)"
            + " OR (:active = false AND a.unassigned_at IS NOT NULL))";

    @Query(value = "SELECT DISTINCT r.*" + ASSIGNED_JOINS + ASSIGNED_WHERE
            + " ORDER BY r.created_at DESC, r.id DESC",
            countQuery = "SELECT count(DISTINCT r.id)" + ASSIGNED_JOINS + ASSIGNED_WHERE,
            nativeQuery = true)
    Page<MaintenanceRequest> findAssignedPage(@Param("workerId") Long workerId,
            @Param("active") boolean active, Pageable pageable);
}

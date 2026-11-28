package com.dormfix.maintenance.infrastructure;

import com.dormfix.catalog.domain.Priority;
import com.dormfix.maintenance.application.DashboardQueryRepository;
import com.dormfix.maintenance.domain.RequestStatus;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Counts through space and building to the dormitory; inactive structure is counted too. */
@Repository
class NativeDashboardQueryRepository implements DashboardQueryRepository {
    private static final String SCOPED = " from maintenance_request r"
            + " join space s on s.id = r.space_id"
            + " join building b on b.id = s.building_id"
            + " where b.dormitory_id in (:dormitoryIds)";
    private static final String ACTIVE = " and r.status not in ('CLOSED', 'REJECTED', 'DUPLICATE')";

    private final EntityManager entityManager;

    NativeDashboardQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Map<RequestStatus, Long> countByStatus(Collection<Long> dormitoryIds) {
        Map<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        for (Object[] row : rows("select r.status, count(*)" + SCOPED + " group by r.status",
                dormitoryIds)) {
            counts.put(RequestStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    public Map<Priority, Long> countActiveByPriority(Collection<Long> dormitoryIds) {
        Map<Priority, Long> counts = new EnumMap<>(Priority.class);
        for (Object[] row : rows("select r.priority, count(*)" + SCOPED + ACTIVE
                + " group by r.priority", dormitoryIds)) {
            counts.put(Priority.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    public long countCreatedSince(Collection<Long> dormitoryIds, Instant since) {
        return count("select count(*)" + SCOPED + " and r.created_at >= :since", dormitoryIds,
                since);
    }

    @Override
    public long countClosedSince(Collection<Long> dormitoryIds, Instant since) {
        return count("select count(*)" + SCOPED + " and r.closed_at >= :since", dormitoryIds,
                since);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Collection<Long> dormitoryIds) {
        return entityManager.createNativeQuery(sql).setParameter("dormitoryIds", dormitoryIds)
                .getResultList();
    }

    private long count(String sql, Collection<Long> dormitoryIds, Instant since) {
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("dormitoryIds", dormitoryIds)
                .setParameter("since", Timestamp.from(since)).getSingleResult()).longValue();
    }
}

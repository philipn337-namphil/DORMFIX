package com.dormfix.maintenance.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET /admin/dashboard/summary (ADR-030). Counts only; never request rows. */
@Service
public class DashboardService {
    private static final Duration WINDOW = Duration.ofDays(7);

    private final DashboardQueryRepository counts;
    private final AdminDormitoryScopeRepository adminScopes;
    private final DormitoryRepository dormitories;
    private final Clock clock;

    public DashboardService(DashboardQueryRepository counts,
            AdminDormitoryScopeRepository adminScopes, DormitoryRepository dormitories,
            Clock clock) {
        this.counts = counts;
        this.adminScopes = adminScopes;
        this.dormitories = dormitories;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResult summary(Long actorId, Set<String> roleNames, Long dormitoryId) {
        Set<Role> roles = roles(roleNames);
        Set<Long> adminScope = roles.contains(Role.ADMIN)
                ? adminScopes.findAllByUserId(actorId).stream()
                        .map(scope -> scope.getDormitoryId()).collect(Collectors.toSet())
                : Set.of();
        Set<Long> existing = roles.contains(Role.SUPER_ADMIN) ? dormitories.findAllIds() : Set.of();
        List<Long> ids = DashboardScope.resolve(roles, adminScope, dormitoryId, existing);

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (ids.isEmpty()) {
            return new DashboardSummaryResult(ids, DashboardCounts.statuses(Map.of()),
                    DashboardCounts.priorities(Map.of()), 0, 0, now);
        }
        Instant since = now.minus(WINDOW);
        return new DashboardSummaryResult(ids,
                DashboardCounts.statuses(counts.countByStatus(ids)),
                DashboardCounts.priorities(counts.countActiveByPriority(ids)),
                counts.countCreatedSince(ids, since), counts.countClosedSince(ids, since), now);
    }

    private static Set<Role> roles(Set<String> names) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : names) {
            try {
                roles.add(Role.valueOf(name));
            } catch (IllegalArgumentException error) {
                throw new DormitoryScopeAccessDeniedException();
            }
        }
        return roles;
    }
}

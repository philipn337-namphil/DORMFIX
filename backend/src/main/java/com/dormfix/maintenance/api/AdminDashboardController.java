package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.DashboardService;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADR-030 administrator dashboard summary. */
@RestController
public class AdminDashboardController {
    private final DashboardService dashboard;

    public AdminDashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/v1/admin/dashboard/summary")
    public DashboardSummaryResponse summary(@RequestParam(required = false) Long dormitoryId,
            @AuthenticationPrincipal Jwt jwt) {
        return DashboardSummaryResponse.from(dashboard.summary(id(jwt), roles(jwt), dormitoryId));
    }

    private Long id(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException error) {
            throw new AccessDeniedException("Access is denied.");
        }
    }

    private Set<String> roles(Jwt jwt) {
        List<String> values = jwt.getClaimAsStringList("roles");
        return values == null ? Set.of() : Set.copyOf(values);
    }
}

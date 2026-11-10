package com.dormfix.maintenance.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves what a caller is to one request. Holding a role is not enough: a WORKER unrelated to the
 * request, or a reporter who also happens to hold WORKER, has no staff relationship to it and must
 * not gain the staff view through the role alone.
 */
@Component
public class RequestRelationshipResolver {
    private final MaintenanceAssignmentRepository assignments;
    private final AdminDormitoryScopeRepository adminScopes;
    private final DormitoryScopeResolutionService scopes;

    public RequestRelationshipResolver(MaintenanceAssignmentRepository assignments,
            AdminDormitoryScopeRepository adminScopes, DormitoryScopeResolutionService scopes) {
        this.assignments = assignments;
        this.adminScopes = adminScopes;
        this.scopes = scopes;
    }

    public RequestRelationship resolve(MaintenanceRequest request, Long actorId, Set<Role> roles) {
        boolean administrator = roles.contains(Role.SUPER_ADMIN)
                || roles.contains(Role.ADMIN) && adminDormitoryIds(actorId)
                        .contains(scopes.resolveDormitoryIdForSpace(request.getSpaceId()));
        boolean currentAssignee = roles.contains(Role.WORKER)
                && assignments.findActiveByRequestId(request.getId())
                        .map(assignment -> assignment.getWorkerId().equals(actorId)).orElse(false);
        boolean everAssigned = roles.contains(Role.WORKER)
                && assignments.existsByRequestIdAndWorkerId(request.getId(), actorId);
        boolean reporter = roles.contains(Role.RESIDENT)
                && request.getReporterId().equals(actorId);
        return new RequestRelationship(reporter, administrator, currentAssignee, everAssigned);
    }

    private Set<Long> adminDormitoryIds(Long actorId) {
        return adminScopes.findAllByUserId(actorId).stream()
                .map(scope -> scope.getDormitoryId()).collect(Collectors.toSet());
    }

    /**
     * {@code staffReader} includes a former assignee, who keeps historical read-only access.
     * {@code staffWriter} does not: a former worker has no write authority.
     */
    public record RequestRelationship(boolean reporter, boolean administrator,
            boolean currentAssignee, boolean everAssigned) {
        public boolean staffReader() {
            return administrator || everAssigned;
        }

        public boolean staffWriter() {
            return administrator || currentAssignee;
        }

        public boolean canRead() {
            return staffReader() || reporter;
        }
    }
}

package com.dormfix.catalog.api;

import com.dormfix.catalog.application.AdminCatalogCommandService;
import com.dormfix.catalog.application.CreateFacilityCommand;
import com.dormfix.catalog.application.CreateMaintenanceCategoryCommand;
import com.dormfix.catalog.application.UpdateFacilityCommand;
import com.dormfix.catalog.application.UpdateMaintenanceCategoryCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final AdminCatalogCommandService commands;

    public AdminCatalogController(AdminCatalogCommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/spaces/{spaceId}/facilities")
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityResponse createFacility(@PathVariable Long spaceId,
            @Valid @RequestBody CreateFacilityRequest request, @AuthenticationPrincipal Jwt jwt) {
        return facility(commands.createFacility(spaceId, new CreateFacilityCommand(request.name(), request.facilityType(),
                request.assetCode(), request.installedAt(), request.description()), userId(jwt), roles(jwt)));
    }

    @PatchMapping("/facilities/{facilityId}")
    public FacilityResponse updateFacility(@PathVariable Long facilityId,
            @Valid @RequestBody UpdateFacilityRequest request, @AuthenticationPrincipal Jwt jwt) {
        return facility(commands.updateFacility(facilityId, new UpdateFacilityCommand(request.name(), request.facilityType(),
                request.assetCode(), request.installedAt(), request.description()), userId(jwt), roles(jwt)));
    }

    @PostMapping("/facilities/{facilityId}/out-of-service")
    public FacilityResponse takeOutOfService(@PathVariable Long facilityId, @AuthenticationPrincipal Jwt jwt) {
        return facility(commands.takeFacilityOutOfService(facilityId, userId(jwt), roles(jwt)));
    }

    @PostMapping("/facilities/{facilityId}/reactivate")
    public FacilityResponse reactivateFacility(@PathVariable Long facilityId, @AuthenticationPrincipal Jwt jwt) {
        return facility(commands.reactivateFacility(facilityId, userId(jwt), roles(jwt)));
    }

    @PostMapping("/facilities/{facilityId}/retire")
    public FacilityResponse retireFacility(@PathVariable Long facilityId, @AuthenticationPrincipal Jwt jwt) {
        return facility(commands.retireFacility(facilityId, userId(jwt), roles(jwt)));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceCategoryResponse createCategory(@Valid @RequestBody CreateMaintenanceCategoryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return category(commands.createCategory(new CreateMaintenanceCategoryCommand(request.parentId(), request.code(),
                request.name(), request.defaultPriority(), request.sortOrder()), roles(jwt)));
    }

    @PatchMapping("/categories/{categoryId}")
    public MaintenanceCategoryResponse updateCategory(@PathVariable Long categoryId,
            @Valid @RequestBody UpdateMaintenanceCategoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return category(commands.updateCategory(categoryId, new UpdateMaintenanceCategoryCommand(request.code(),
                request.name(), request.defaultPriority(), request.sortOrder()), roles(jwt)));
    }

    @PostMapping("/categories/{categoryId}/deactivate")
    public MaintenanceCategoryResponse deactivateCategory(@PathVariable Long categoryId,
            @AuthenticationPrincipal Jwt jwt) {
        return category(commands.deactivateCategory(categoryId, roles(jwt)));
    }

    @PostMapping("/categories/{categoryId}/reactivate")
    public MaintenanceCategoryResponse reactivateCategory(@PathVariable Long categoryId,
            @AuthenticationPrincipal Jwt jwt) {
        return category(commands.reactivateCategory(categoryId, roles(jwt)));
    }

    private FacilityResponse facility(com.dormfix.catalog.application.FacilityCommandResult value) {
        return new FacilityResponse(value.id(), value.spaceId(), value.name(), value.facilityType(), value.assetCode(),
                value.status(), value.installedAt(), value.description(), value.createdAt(), value.updatedAt());
    }

    private MaintenanceCategoryResponse category(
            com.dormfix.catalog.application.MaintenanceCategoryCommandResult value) {
        return new MaintenanceCategoryResponse(value.id(), value.parentId(), value.code(), value.name(),
                value.defaultPriority(), value.active(), value.sortOrder(), value.createdAt(), value.updatedAt());
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException error) {
            throw new org.springframework.security.access.AccessDeniedException("Access is denied.");
        }
    }

    private Set<String> roles(Jwt jwt) {
        List<String> claimed = jwt.getClaimAsStringList("roles");
        if (claimed == null) {
            return Set.of();
        }
        try {
            return Set.copyOf(claimed);
        } catch (IllegalArgumentException error) {
            throw new org.springframework.security.access.AccessDeniedException("Access is denied.");
        }
    }
}

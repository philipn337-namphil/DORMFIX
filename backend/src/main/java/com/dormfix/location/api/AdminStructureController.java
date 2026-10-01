package com.dormfix.location.api;

import com.dormfix.location.application.AdminStructureCommandService;
import com.dormfix.location.application.CreateBuildingCommand;
import com.dormfix.location.application.CreateDormitoryCommand;
import com.dormfix.location.application.CreateSpaceCommand;
import com.dormfix.location.application.UpdateBuildingCommand;
import com.dormfix.location.application.UpdateDormitoryCommand;
import com.dormfix.location.application.UpdateSpaceCommand;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminStructureController {
    private final AdminStructureCommandService commands;

    public AdminStructureController(AdminStructureCommandService commands) {
        this.commands = commands;
    }

    @PostMapping("/dormitories")
    @ResponseStatus(HttpStatus.CREATED)
    public DormitoryResponse createDormitory(@Valid @RequestBody CreateDormitoryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return DormitoryResponse.from(commands.createDormitory(
                new CreateDormitoryCommand(request.name(), request.address(), request.timezone()), roles(jwt)));
    }

    @PatchMapping("/dormitories/{dormitoryId}")
    public DormitoryResponse updateDormitory(@PathVariable Long dormitoryId,
            @Valid @RequestBody UpdateDormitoryRequest request, @AuthenticationPrincipal Jwt jwt) {
        return DormitoryResponse.from(commands.updateDormitory(dormitoryId,
                new UpdateDormitoryCommand(request.name(), request.address(), request.timezone()), roles(jwt)));
    }

    @PostMapping("/dormitories/{dormitoryId}/deactivate")
    public DormitoryResponse deactivateDormitory(@PathVariable Long dormitoryId,
            @AuthenticationPrincipal Jwt jwt) {
        return DormitoryResponse.from(commands.deactivateDormitory(dormitoryId, roles(jwt)));
    }

    @PostMapping("/dormitories/{dormitoryId}/reactivate")
    public DormitoryResponse reactivateDormitory(@PathVariable Long dormitoryId,
            @AuthenticationPrincipal Jwt jwt) {
        return DormitoryResponse.from(commands.reactivateDormitory(dormitoryId, roles(jwt)));
    }

    @PostMapping("/dormitories/{dormitoryId}/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingResponse createBuilding(@PathVariable Long dormitoryId,
            @Valid @RequestBody CreateBuildingRequest request, @AuthenticationPrincipal Jwt jwt) {
        return BuildingResponse.from(commands.createBuilding(dormitoryId,
                new CreateBuildingCommand(request.code(), request.name()), roles(jwt)));
    }

    @PatchMapping("/buildings/{buildingId}")
    public BuildingResponse updateBuilding(@PathVariable Long buildingId,
            @Valid @RequestBody UpdateBuildingRequest request, @AuthenticationPrincipal Jwt jwt) {
        return BuildingResponse.from(commands.updateBuilding(buildingId,
                new UpdateBuildingCommand(request.code(), request.name()), roles(jwt)));
    }

    @PostMapping("/buildings/{buildingId}/deactivate")
    public BuildingResponse deactivateBuilding(@PathVariable Long buildingId,
            @AuthenticationPrincipal Jwt jwt) {
        return BuildingResponse.from(commands.deactivateBuilding(buildingId, roles(jwt)));
    }

    @PostMapping("/buildings/{buildingId}/reactivate")
    public BuildingResponse reactivateBuilding(@PathVariable Long buildingId,
            @AuthenticationPrincipal Jwt jwt) {
        return BuildingResponse.from(commands.reactivateBuilding(buildingId, roles(jwt)));
    }

    @PostMapping("/buildings/{buildingId}/spaces")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse createSpace(@PathVariable Long buildingId,
            @Valid @RequestBody CreateSpaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return SpaceResponse.from(commands.createSpace(buildingId,
                new CreateSpaceCommand(request.code(), request.name(), request.type(), request.floor(),
                        request.description()), roles(jwt)));
    }

    @PatchMapping("/spaces/{spaceId}")
    public SpaceResponse updateSpace(@PathVariable Long spaceId,
            @Valid @RequestBody UpdateSpaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return SpaceResponse.from(commands.updateSpace(spaceId,
                new UpdateSpaceCommand(request.code(), request.name(), request.type(), request.floor(),
                        request.description()), roles(jwt)));
    }

    @PostMapping("/spaces/{spaceId}/deactivate")
    public SpaceResponse deactivateSpace(@PathVariable Long spaceId, @AuthenticationPrincipal Jwt jwt) {
        return SpaceResponse.from(commands.deactivateSpace(spaceId, roles(jwt)));
    }

    @PostMapping("/spaces/{spaceId}/reactivate")
    public SpaceResponse reactivateSpace(@PathVariable Long spaceId, @AuthenticationPrincipal Jwt jwt) {
        return SpaceResponse.from(commands.reactivateSpace(spaceId, roles(jwt)));
    }

    private Set<String> roles(Jwt jwt) {
        List<String> claimedRoles = jwt.getClaimAsStringList("roles");
        if (claimedRoles == null) {
            return Set.of();
        }
        return Arrays.stream(claimedRoles.toArray(String[]::new)).collect(Collectors.toUnmodifiableSet());
    }
}

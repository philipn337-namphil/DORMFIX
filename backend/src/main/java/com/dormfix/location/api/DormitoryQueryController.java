package com.dormfix.location.api;

import com.dormfix.location.application.DormitoryQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DormitoryQueryController {
    private final DormitoryQueryService queryService;

    public DormitoryQueryController(DormitoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/dormitories")
    public List<DormitoryResponse> dormitories() {
        return queryService.findActiveDormitories().stream().map(DormitoryResponse::from).toList();
    }

    @GetMapping("/dormitories/{dormitoryId}/buildings")
    public List<BuildingResponse> buildings(@PathVariable Long dormitoryId) {
        return queryService.findActiveBuildings(dormitoryId).stream().map(BuildingResponse::from).toList();
    }

    @GetMapping("/buildings/{buildingId}/spaces")
    public List<SpaceResponse> spaces(@PathVariable Long buildingId) {
        return queryService.findActiveSpaces(buildingId).stream().map(SpaceResponse::from).toList();
    }

    @GetMapping("/spaces/{spaceId}")
    public SpaceResponse space(@PathVariable Long spaceId) {
        return SpaceResponse.from(queryService.findSpace(spaceId));
    }
}

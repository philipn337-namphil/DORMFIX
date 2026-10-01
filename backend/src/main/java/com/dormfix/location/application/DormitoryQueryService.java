package com.dormfix.location.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DormitoryQueryService {
    private final DormitoryRepository dormitories;
    private final BuildingRepository buildings;
    private final SpaceRepository spaces;

    public DormitoryQueryService(DormitoryRepository dormitories, BuildingRepository buildings,
            SpaceRepository spaces) {
        this.dormitories = dormitories;
        this.buildings = buildings;
        this.spaces = spaces;
    }

    @Transactional(readOnly = true)
    public List<DormitoryQueryResult> findActiveDormitories() {
        return dormitories.findAllByActiveTrueOrderByNameAsc().stream()
                .map(dormitory -> new DormitoryQueryResult(dormitory.getId(), dormitory.getName(),
                        dormitory.getAddress(), dormitory.getTimezone(), dormitory.isActive(),
                        dormitory.getCreatedAt(), dormitory.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BuildingQueryResult> findActiveBuildings(Long dormitoryId) {
        requireDormitory(dormitoryId);
        return buildings.findAllByDormitoryIdAndActiveTrueOrderByCodeAsc(dormitoryId).stream()
                .map(building -> new BuildingQueryResult(building.getId(), building.getDormitoryId(),
                        building.getCode(), building.getName(), building.isActive(), building.getCreatedAt(),
                        building.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceQueryResult> findActiveSpaces(Long buildingId) {
        requireBuilding(buildingId);
        return spaces.findAllByBuildingIdAndActiveTrueOrderByCodeAsc(buildingId).stream()
                .map(space -> new SpaceQueryResult(space.getId(), space.getBuildingId(), space.getCode(),
                        space.getName(), space.getType().name(), space.getFloor(), space.getDescription(),
                        space.isActive(), space.getCreatedAt(), space.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SpaceQueryResult findSpace(Long spaceId) {
        var space = spaces.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
        return new SpaceQueryResult(space.getId(), space.getBuildingId(), space.getCode(), space.getName(),
                space.getType().name(), space.getFloor(), space.getDescription(), space.isActive(),
                space.getCreatedAt(), space.getUpdatedAt());
    }

    private void requireDormitory(Long id) {
        if (!dormitories.findById(id).isPresent()) {
            throw new DormitoryNotFoundException(id);
        }
    }

    private void requireBuilding(Long id) {
        if (!buildings.findById(id).isPresent()) {
            throw new BuildingNotFoundException(id);
        }
    }
}

package com.dormfix.catalog.application;

import com.dormfix.location.application.SpaceNotFoundException;
import com.dormfix.location.application.SpaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogQueryService {
    private final FacilityRepository facilities;
    private final MaintenanceCategoryRepository categories;
    private final SpaceRepository spaces;

    public CatalogQueryService(FacilityRepository facilities,
            MaintenanceCategoryRepository categories, SpaceRepository spaces) {
        this.facilities = facilities;
        this.categories = categories;
        this.spaces = spaces;
    }

    @Transactional(readOnly = true)
    public List<FacilityQueryResult> findFacilities(Long spaceId) {
        if (!spaces.existsById(spaceId)) {
            throw new SpaceNotFoundException(spaceId);
        }
        return facilities.findAllBySpaceIdOrderByNameAsc(spaceId).stream()
                .map(facility -> new FacilityQueryResult(facility.getId(), facility.getSpaceId(),
                        facility.getName(), facility.getFacilityType(), facility.getAssetCode(),
                        facility.getStatus().name(), facility.getInstalledAt(), facility.getDescription(),
                        facility.getCreatedAt(), facility.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MaintenanceCategoryQueryResult> findActiveCategories() {
        return categories.findAllByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                .map(category -> new MaintenanceCategoryQueryResult(category.getId(), category.getParentId(),
                        category.getCode(), category.getName(), category.getDefaultPriority().name(),
                        category.isActive(), category.getSortOrder(), category.getCreatedAt(),
                        category.getUpdatedAt()))
                .toList();
    }
}

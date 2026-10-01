package com.dormfix.catalog.api;

import com.dormfix.catalog.application.CatalogQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogQueryController {
    private final CatalogQueryService queryService;

    public CatalogQueryController(CatalogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/spaces/{spaceId}/facilities")
    public List<FacilityResponse> facilities(@PathVariable Long spaceId) {
        return queryService.findFacilities(spaceId).stream().map(FacilityResponse::from).toList();
    }

    @GetMapping("/categories")
    public List<MaintenanceCategoryResponse> categories() {
        return queryService.findActiveCategories().stream()
                .map(MaintenanceCategoryResponse::from)
                .toList();
    }
}

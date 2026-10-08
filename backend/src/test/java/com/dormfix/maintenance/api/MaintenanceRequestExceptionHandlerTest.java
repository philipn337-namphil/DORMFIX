package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.location.application.BuildingNotFoundException;
import com.dormfix.location.application.DormitoryNotFoundException;
import com.dormfix.platform.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MaintenanceRequestExceptionHandlerTest {
    private final MaintenanceRequestExceptionHandler handler = new MaintenanceRequestExceptionHandler();

    @Test
    void mapsMissingBuildingToItsRepresentativeCode() {
        ApiError error = body(handler.notFound(new BuildingNotFoundException(42L), request()));

        assertThat(error.code()).isEqualTo("BUILDING_NOT_FOUND");
    }

    @Test
    void mapsMissingDormitoryToItsRepresentativeCode() {
        ApiError error = body(handler.notFound(new DormitoryNotFoundException(42L), request()));

        assertThat(error.code()).isEqualTo("DORMITORY_NOT_FOUND");
    }

    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/maintenance-requests");
        return request;
    }

    private ApiError body(org.springframework.http.ResponseEntity<ApiError> response) {
        return response.getBody();
    }
}

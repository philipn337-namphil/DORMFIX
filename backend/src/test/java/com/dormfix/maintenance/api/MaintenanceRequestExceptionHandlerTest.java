package com.dormfix.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.location.application.BuildingNotFoundException;
import com.dormfix.location.application.DormitoryNotFoundException;
import com.dormfix.maintenance.application.InvalidMaintenanceQueryException;
import com.dormfix.maintenance.application.MaintenanceRequestNotFoundException;
import com.dormfix.maintenance.application.WorkerNotFoundException;
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

    @Test
    void mapsMissingRequestAndWorkerToTheirFrozenCodes() {
        assertThat(body(handler.notFound(new MaintenanceRequestNotFoundException(1L), request()))
                .code()).isEqualTo("REQUEST_NOT_FOUND");
        assertThat(body(handler.notFound(new WorkerNotFoundException(2L), request())).code())
                .isEqualTo("WORKER_NOT_FOUND");
    }

    @Test
    void refusesToGuessACodeForAnUnmappedNotFoundType() {
        assertThatThrownBy(() -> handler.notFound(new IllegalStateException("x"), request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unmapped not-found exception");
    }

    @Test
    void separatesTheAssignmentConflictsFromVersionConflict() {
        assertThat(body(handler.alreadyAssigned(request())).code())
                .isEqualTo("REQUEST_ALREADY_ASSIGNED");
        assertThat(body(handler.invalidWorker(request())).code()).isEqualTo("INVALID_WORKER");
        assertThat(body(handler.versionConflict(request())).code()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void assignmentConflictsAnswerWith409() {
        assertThat(handler.alreadyAssigned(request()).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.invalidWorker(request()).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.versionConflict(request()).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void invalidQueryAnswersTheStandard400Contract() {
        var response = handler.invalidQuery(new InvalidMaintenanceQueryException("Invalid pagination."),
                request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response).code()).isEqualTo("VALIDATION_FAILED");
        assertThat(body(response).message()).isEqualTo("Invalid pagination.");
    }

    @Test
    void unexpectedFailuresKeepTheStandardErrorContractInsteadOfAContainerBody() {
        var response = handler.unexpected(request());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(body(response).code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body(response).fieldErrors()).isEmpty();
    }

    @Test
    void dataIntegrityFailuresAnswerAStateConflictWithACode() {
        var response = handler.dataIntegrity(request());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).code()).isEqualTo("INVALID_REQUEST_STATE");
    }

    @Test
    void separatesWorkerNotAssignedFromAccessDenied() {
        var notAssigned = handler.workerNotAssigned(request());
        var denied = handler.forbidden(request());

        assertThat(notAssigned.getStatusCode().value()).isEqualTo(403);
        assertThat(body(notAssigned).code()).isEqualTo("WORKER_NOT_ASSIGNED");
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(body(denied).code()).isEqualTo("ACCESS_DENIED");
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

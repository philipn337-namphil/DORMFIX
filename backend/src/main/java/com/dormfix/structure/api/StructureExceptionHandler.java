package com.dormfix.structure.api;

import com.dormfix.catalog.api.CatalogQueryController;
import com.dormfix.catalog.api.AdminCatalogController;
import com.dormfix.catalog.application.MaintenanceCategoryNotFoundException;
import com.dormfix.location.api.DormitoryQueryController;
import com.dormfix.location.api.AdminStructureController;
import com.dormfix.location.api.ResidenceController;
import com.dormfix.location.application.ResidenceNotFoundException;
import com.dormfix.identity.application.UserNotFoundException;
import com.dormfix.location.application.StructureStateConflictException;
import com.dormfix.location.application.StructureValidationException;
import com.dormfix.location.application.BuildingNotFoundException;
import com.dormfix.location.application.DormitoryNotFoundException;
import com.dormfix.location.application.FacilityNotFoundException;
import com.dormfix.location.application.SpaceNotFoundException;
import com.dormfix.platform.api.ApiError;
import com.dormfix.platform.api.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Comparator;

@RestControllerAdvice(assignableTypes = {DormitoryQueryController.class, CatalogQueryController.class,
        AdminStructureController.class, AdminCatalogController.class, ResidenceController.class})
public class StructureExceptionHandler {
    @ExceptionHandler(DormitoryNotFoundException.class)
    ResponseEntity<ApiError> dormitoryNotFound(HttpServletRequest request) {
        return notFound(request, "DORMITORY_NOT_FOUND", "Dormitory was not found.");
    }

    @ExceptionHandler(BuildingNotFoundException.class)
    ResponseEntity<ApiError> buildingNotFound(HttpServletRequest request) {
        return notFound(request, "BUILDING_NOT_FOUND", "Building was not found.");
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    ResponseEntity<ApiError> spaceNotFound(HttpServletRequest request) {
        return notFound(request, "SPACE_NOT_FOUND", "Space was not found.");
    }

    @ExceptionHandler(FacilityNotFoundException.class)
    ResponseEntity<ApiError> facilityNotFound(HttpServletRequest request) {
        return notFound(request, "FACILITY_NOT_FOUND", "Facility was not found.");
    }

    @ExceptionHandler(MaintenanceCategoryNotFoundException.class)
    ResponseEntity<ApiError> categoryNotFound(HttpServletRequest request) {
        return notFound(request, "CATEGORY_NOT_FOUND", "Maintenance category was not found.");
    }
    @ExceptionHandler(ResidenceNotFoundException.class)
    ResponseEntity<ApiError> residenceNotFound(HttpServletRequest request) { return notFound(request, "RESIDENCE_NOT_FOUND", "Residence was not found."); }
    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> userNotFound(HttpServletRequest request) { return notFound(request, "USER_NOT_FOUND", "User was not found."); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.", List.of());
    }

    @ExceptionHandler(StructureStateConflictException.class)
    ResponseEntity<ApiError> stateConflict(StructureStateConflictException error, HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, error.getCode(), error.getMessage(), List.of());
    }

    @ExceptionHandler(StructureValidationException.class)
    ResponseEntity<ApiError> structureValidation(StructureValidationException error, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", error.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> structureConflict(DataIntegrityViolationException error, HttpServletRequest request) {
        if (hasResidenceOverlapConstraint(error)) {
            return response(request, HttpStatus.CONFLICT, "RESIDENCE_PERIOD_OVERLAP",
                    "Residence period overlaps an existing residence.", List.of());
        }
        return response(request, HttpStatus.CONFLICT, "STRUCTURE_CONFLICT",
                "The structure could not be saved because it conflicts with existing data.", List.of());
    }

    private boolean hasResidenceOverlapConstraint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("ex_residence_resident_period")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException error, HttpServletRequest request) {
        List<ApiError.FieldError> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiError.FieldError(field.getField(), field.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request contains invalid fields.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "The request body is invalid.", List.of());
    }

    private ResponseEntity<ApiError> notFound(HttpServletRequest request, String code, String message) {
        return response(request, HttpStatus.NOT_FOUND, code, message, List.of());
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
            String code, String message, List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), (String) request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE), fields));
    }
}

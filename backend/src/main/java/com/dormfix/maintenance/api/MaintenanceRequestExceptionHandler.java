package com.dormfix.maintenance.api;

import com.dormfix.catalog.application.MaintenanceCategoryNotFoundException;
import com.dormfix.identity.application.UserNotFoundException;
import com.dormfix.location.application.BuildingNotFoundException;
import com.dormfix.location.application.DormitoryNotFoundException;
import com.dormfix.location.application.FacilityNotFoundException;
import com.dormfix.location.application.SpaceNotFoundException;
import com.dormfix.maintenance.application.InvalidEntryPolicyException;
import com.dormfix.maintenance.application.InvalidPreferredVisitTimeException;
import com.dormfix.maintenance.application.MaintenanceRequestConflictException;
import com.dormfix.platform.api.ApiError;
import com.dormfix.platform.api.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MaintenanceRequestController.class)
public class MaintenanceRequestExceptionHandler {
    @ExceptionHandler({SpaceNotFoundException.class, BuildingNotFoundException.class,
            DormitoryNotFoundException.class, FacilityNotFoundException.class,
            MaintenanceCategoryNotFoundException.class, UserNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException error, HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, code(error), "Resource was not found.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.", List.of());
    }

    @ExceptionHandler(MaintenanceRequestConflictException.class)
    ResponseEntity<ApiError> conflict(MaintenanceRequestConflictException error,
            HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "INVALID_REQUEST_STATE", error.getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidEntryPolicyException.class)
    ResponseEntity<ApiError> invalidEntryPolicy(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_ENTRY_POLICY", "Invalid entry policy.",
                List.of());
    }

    @ExceptionHandler(InvalidPreferredVisitTimeException.class)
    ResponseEntity<ApiError> invalidPreferredVisitTime(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_VISIT_TIME",
                "Invalid preferred visit time.", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException error,
            HttpServletRequest request) {
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

    private String code(RuntimeException error) {
        if (error instanceof SpaceNotFoundException) {
            return "SPACE_NOT_FOUND";
        }
        if (error instanceof BuildingNotFoundException) {
            return "BUILDING_NOT_FOUND";
        }
        if (error instanceof DormitoryNotFoundException) {
            return "DORMITORY_NOT_FOUND";
        }
        if (error instanceof FacilityNotFoundException) {
            return "FACILITY_NOT_FOUND";
        }
        if (error instanceof MaintenanceCategoryNotFoundException) {
            return "CATEGORY_NOT_FOUND";
        }
        if (error instanceof UserNotFoundException) {
            return "USER_NOT_FOUND";
        }
        return "SPACE_NOT_FOUND";
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
            String code, String message, List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code,
                message, request.getRequestURI(),
                (String) request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE), fields));
    }
}

package com.forumx.common.exception;

import java.util.HashMap;
import java.util.Map;

import com.forumx.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Validation ──────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                extractPath(request),
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errorResponse, extractPath(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request
    ) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            validationErrors.put(field, violation.getMessage());
        });

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                "Constraint violation",
                extractPath(request),
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Constraint violation", errorResponse, extractPath(request)));
    }

    // ── Client errors ───────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, extractPath(request)));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleEntityNotFound(
            EntityNotFoundException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage(),
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, extractPath(request)));
    }

    // ── Security ────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.FORBIDDEN,
                "You do not have permission to access this resource",
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Forbidden", errorResponse, extractPath(request)));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUsernameNotFound(
            UsernameNotFoundException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVALID_CREDENTIALS,
                "Invalid username or password",
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed", errorResponse, extractPath(request)));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleBadCredentials(
            BadCredentialsException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVALID_CREDENTIALS,
                "Invalid username or password",
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed", errorResponse, extractPath(request)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAuthentication(
            AuthenticationException ex,
            WebRequest request
    ) {
        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.UNAUTHORIZED,
                "Authentication required",
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Unauthorized", errorResponse, extractPath(request)));
    }

    // ── Catch-all ───────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAll(
            Exception ex,
            WebRequest request
    ) {
        LOGGER.error("Unhandled exception at path={}", extractPath(request), ex);

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                extractPath(request),
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", errorResponse, extractPath(request)));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private ErrorResponse buildErrorResponse(
            ErrorCode errorCode,
            String message,
            String path,
            Map<String, String> validationErrors
    ) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}

package com.forumx.common.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.forumx.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import io.jsonwebtoken.JwtException;

/**
 * Centralized global exception handler for handling web request processing failures
 * outside the Spring Security filters pipeline.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    /**
     * Handles MethodArgumentNotValidException for DTO field validation failures.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(error.getField(), error.getDefaultMessage())
        );

        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Validation failed: " + validationErrors));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                path,
                requestId,
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errorResponse, path));
    }

    /**
     * Handles ConstraintViolationException for model/entity validation failures.
     */
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

        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Constraint violation failed: " + validationErrors));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed",
                path,
                requestId,
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errorResponse, path));
    }

    /**
     * Handles HttpMessageNotReadableException for malformed request payloads.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Malformed request body"));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                "Malformed request body.",
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Bad request", errorResponse, path));
    }

    /**
     * Handles MissingServletRequestParameterException for missing query parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Missing request parameter: " + ex.getParameterName()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Bad request", errorResponse, path));
    }

    /**
     * Handles MissingRequestHeaderException for missing request headers.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMissingRequestHeader(
            MissingRequestHeaderException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Missing request header: " + ex.getHeaderName()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Bad request", errorResponse, path));
    }

    /**
     * Handles IllegalArgumentException for invalid method parameters.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Illegal argument: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles InvalidReportStateException for report status modification violations.
     */
    @ExceptionHandler(InvalidReportStateException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidReportState(
            InvalidReportStateException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invalid report state: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles InvalidSearchQueryException.
     */
    @ExceptionHandler(InvalidSearchQueryException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidSearchQuery(
            InvalidSearchQueryException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invalid search query: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles BadCredentialsException for authentication failures.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleBadCredentials(
            BadCredentialsException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Authentication failed: Bad credentials"));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVALID_CREDENTIALS,
                "Invalid username/email or password.",
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed", errorResponse, path));
    }

    /**
     * Handles DisabledException, LockedException, etc. for account/tenant status failures.
     */
    @ExceptionHandler({DisabledException.class, LockedException.class, AccountExpiredException.class, CredentialsExpiredException.class})
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAccountStatusException(
            AuthenticationException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Authentication failed - account/tenant status: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.ACCOUNT_DISABLED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles AccessDeniedException for authorization failures.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Access denied: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.ACCESS_DENIED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", errorResponse, path));
    }

    /**
     * Handles EntityNotFoundException when a requested database entity is not found.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleEntityNotFound(
            EntityNotFoundException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Entity not found: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Response status exception: " + ex.getReason()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getReason() != null ? ex.getReason() : "Error", errorResponse, path));
    }

    /**
     * Handles DataIntegrityViolationException to prevent DB leaks.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Database conflict: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.CONFLICT,
                "A database conflict occurred.",
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Conflict occurred", errorResponse, path));
    }

    /**
     * Handles HttpRequestMethodNotSupportedException for invalid HTTP methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "HTTP method not supported: " + ex.getMethod()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("Method not allowed", errorResponse, path));
    }

    /**
     * Handles HttpMediaTypeNotSupportedException for invalid Content-Types.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "HTTP media type not supported: " + ex.getContentType()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Unsupported media type", errorResponse, path));
    }

    /**
     * Handles EmailNotVerifiedException when a user attempts to login with an unverified email.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleEmailNotVerified(
            EmailNotVerifiedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Email not verified: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.EMAIL_NOT_VERIFIED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles InvalidTokenException.
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidToken(
            InvalidTokenException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invalid token: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.TOKEN_INVALID,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles ExpiredTokenException.
     */
    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleExpiredToken(
            ExpiredTokenException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Expired token: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.TOKEN_EXPIRED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles TokenAlreadyUsedException.
     */
    @ExceptionHandler(TokenAlreadyUsedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTokenAlreadyUsed(
            TokenAlreadyUsedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Token already used: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles AlreadyVerifiedException.
     */
    @ExceptionHandler(AlreadyVerifiedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAlreadyVerified(
            AlreadyVerifiedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Already verified: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles TicketClosedException.
     */
    @ExceptionHandler(TicketClosedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTicketClosed(
            TicketClosedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Ticket closed restriction: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles UsernameAlreadyExistsException.
     */
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUsernameAlreadyExists(
            UsernameAlreadyExistsException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Username already exists: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.USERNAME_ALREADY_EXISTS,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles TenantSlugAlreadyExistsException.
     */
    @ExceptionHandler(TenantSlugAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTenantSlugAlreadyExists(
            TenantSlugAlreadyExistsException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Tenant slug already exists: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.TENANT_SLUG_ALREADY_EXISTS,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles ReservedTenantSlugException.
     */
    @ExceptionHandler(ReservedTenantSlugException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleReservedTenantSlug(
            ReservedTenantSlugException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Reserved tenant slug: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.RESERVED_TENANT_SLUG,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles InvalidTenantSlugException.
     */
    @ExceptionHandler(InvalidTenantSlugException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvalidTenantSlug(
            InvalidTenantSlugException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invalid tenant slug: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVALID_TENANT_SLUG,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles PasswordMismatchException.
     */
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePasswordMismatch(
            PasswordMismatchException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Password confirmation mismatch: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.PASSWORDS_DO_NOT_MATCH,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles PasswordReuseException.
     */
    @ExceptionHandler(PasswordReuseException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePasswordReuse(
            PasswordReuseException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Password reuse attempt: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.PASSWORD_REUSE,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles PasswordNotSetException.
     */
    @ExceptionHandler(PasswordNotSetException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePasswordNotSet(
            PasswordNotSetException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Password not set: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.PASSWORD_NOT_SET,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles DomainIntegrityException for backend data corruption or invariants violation.
     */
    @ExceptionHandler(DomainIntegrityException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleDomainIntegrity(
            DomainIntegrityException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.error(formatLogMessage(request, "Domain integrity violation: " + ex.getMessage()), ex);

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.DOMAIN_INTEGRITY_VIOLATION,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", errorResponse, path));
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvitationNotFound(
            InvitationNotFoundException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invitation not found: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVITATION_NOT_FOUND,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    @ExceptionHandler(InvitationAlreadyAcceptedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvitationAlreadyAccepted(
            InvitationAlreadyAcceptedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invitation already accepted: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVITATION_ALREADY_ACCEPTED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    @ExceptionHandler(InvitationAlreadyPendingException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvitationAlreadyPending(
            InvitationAlreadyPendingException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invitation already pending: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVITATION_ALREADY_PENDING,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles expired and invalid JWT authentication tokens.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleJwtException(
            JwtException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "JWT verification failed: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.UNAUTHORIZED,
                "Invalid or expired authentication token.",
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Unauthorized", errorResponse, path));
    }

    // ── Phase C: Staff Management Exceptions ────────────────────────────

    /**
     * Handles ModeratorNotFoundException when a target user is not a moderator in the tenant.
     */
    @ExceptionHandler(ModeratorNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleModeratorNotFound(
            ModeratorNotFoundException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Moderator not found: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.MODERATOR_NOT_FOUND,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles InvitationAlreadyRevokedException.
     */
    @ExceptionHandler(InvitationAlreadyRevokedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleInvitationAlreadyRevoked(
            InvitationAlreadyRevokedException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Invitation already revoked: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INVITATION_ALREADY_REVOKED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles ModeratorAlreadyDisabledException.
     */
    @ExceptionHandler(ModeratorAlreadyDisabledException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleModeratorAlreadyDisabled(
            ModeratorAlreadyDisabledException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Moderator already disabled: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.MODERATOR_ALREADY_DISABLED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Handles ModeratorAlreadyEnabledException.
     */
    @ExceptionHandler(ModeratorAlreadyEnabledException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleModeratorAlreadyEnabled(
            ModeratorAlreadyEnabledException ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.warn(formatLogMessage(request, "Moderator already enabled: " + ex.getMessage()));

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.MODERATOR_ALREADY_ENABLED,
                ex.getMessage(),
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), errorResponse, path));
    }

    /**
     * Catch-all exception handler for general system failures.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAll(
            Exception ex,
            WebRequest request
    ) {
        String path = resolvePath(request);
        String requestId = resolveRequestId(request);
        log.error(formatLogMessage(request, "Unhandled server exception occurred"), ex);

        ErrorResponse errorResponse = buildErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                path,
                requestId,
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", errorResponse, path));
    }

    /**
     * Helper to build an ErrorResponse payload.
     *
     * @param errorCode        the custom ErrorCode enum
     * @param message          user-facing error message
     * @param path             request URI path
     * @param requestId        request correlation ID
     * @param validationErrors field validation errors map
     * @return constructed ErrorResponse
     */
    private ErrorResponse buildErrorResponse(
            ErrorCode errorCode,
            String message,
            String path,
            String requestId,
            Map<String, String> validationErrors
    ) {
        return ErrorResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .requestId(requestId)
                .timestamp(currentTimestamp())
                .validationErrors(validationErrors)
                .build();
    }

    /**
     * Extracts Request correlation ID from request headers.
     *
     * @param request current web request context
     * @return resolved correlation ID, or null
     */
    private String resolveRequestId(WebRequest request) {
        return request.getHeader("X-Request-ID");
    }

    /**
     * Resolves the request URI path.
     *
     * @param request current web request context
     * @return cleaned request path string
     */
    private String resolvePath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    /**
     * Resolves current instant using the injected Clock bean.
     *
     * @return active timestamp Instant
     */
    private Instant currentTimestamp() {
        return Instant.now(clock);
    }

    /**
     * Formats logging payloads containing path, method, and request correlation details.
     *
     * @param request current web request context
     * @param message log description detail
     * @return formatted log string
     */
    private String formatLogMessage(WebRequest request, String message) {
        String requestId = resolveRequestId(request);
        String path = resolvePath(request);
        String method = "UNKNOWN";
        if (request instanceof ServletWebRequest servletWebRequest) {
            method = servletWebRequest.getRequest().getMethod();
        }
        return String.format("[%s] %s %s - %s", requestId != null ? requestId : "no-id", method, path, message);
    }
}

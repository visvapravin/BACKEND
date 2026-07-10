package com.forumx.auth.controller;

import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.LogoutRequest;
import com.forumx.auth.dto.request.RefreshTokenRequest;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.auth.dto.response.RefreshTokenResponse;
import com.forumx.auth.dto.response.RegisterResponse;
import com.forumx.auth.service.AuthenticationService;
import com.forumx.common.dto.ApiResponse;
import com.forumx.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller exposing REST APIs for user registration, authentication, token refresh, and logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Authentication", description = "Authentication APIs for ForumX")
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Registers a new user within a tenant.
     *
     * @param request the registration details DTO
     * @return response entity containing API response with registration results
     */
    @PostMapping(
            value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Register a new user",
            description = "Registers a new user within the specified tenant slug, creates user profile and assigns default USER role."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Registration successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Success Example",
                                    value = "{\"success\":true,\"message\":\"Registration successful\",\"data\":{\"userId\":1,\"username\":\"johndoe\",\"email\":\"johndoe@example.com\",\"tenantId\":1,\"message\":\"User registered successfully\"},\"timestamp\":\"2026-07-10T16:50:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid validation parameters or password mismatch",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Validation Error Example",
                                    value = "{\"success\":false,\"message\":\"Validation failed\",\"data\":{\"success\":false,\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"Validation failed\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\",\"validationErrors\":{\"email\":\"Must be a valid email address\"}},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Unauthorized Error Example",
                                    value = "{\"success\":false,\"message\":\"Unauthorized\",\"data\":{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Tenant is inactive or registration forbidden",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Forbidden Example",
                                    value = "{\"success\":false,\"message\":\"Tenant is inactive\",\"data\":{\"success\":false,\"errorCode\":\"FORBIDDEN\",\"message\":\"Tenant is inactive\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Specified Tenant slug does not exist",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Tenant Not Found Example",
                                    value = "{\"success\":false,\"message\":\"Tenant not found\",\"data\":{\"success\":false,\"errorCode\":\"TENANT_NOT_FOUND\",\"message\":\"Tenant not found\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Username or email is already taken in the tenant",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Conflict Example",
                                    value = "{\"success\":false,\"message\":\"Username already exists\",\"data\":{\"success\":false,\"errorCode\":\"CONFLICT\",\"message\":\"Username already exists\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Register Internal Server Error Example",
                                    value = "{\"success\":false,\"message\":\"Internal server error\",\"data\":{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred. Please try again later.\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/register\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Registration details for the new user, including tenant selection and profile details.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RegisterRequest.class),
                            examples = @ExampleObject(
                                    name = "Registration Request Payload",
                                    value = "{\"username\":\"johndoe\",\"email\":\"johndoe@example.com\",\"password\":\"SecurePassword123!\",\"confirmPassword\":\"SecurePassword123!\",\"tenantSlug\":\"default\",\"firstName\":\"John\",\"lastName\":\"Doe\",\"displayName\":\"John Doe\",\"phoneNumber\":\"+1234567890\",\"company\":\"ForumX Inc.\",\"department\":\"Engineering\",\"jobTitle\":\"Software Architect\",\"timezone\":\"UTC\",\"locale\":\"en_US\"}"
                            )
                    )
            )
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response = authenticationService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Registration successful",
                        response));
    }

    /**
     * Authenticates a user's credentials and logs them in.
     *
     * @param request        the credentials payload DTO
     * @param servletRequest the HTTP Servlet Request context
     * @return response entity containing API response with authentication token details
     */
    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Login user",
            description = "Authenticates user credentials against the specified tenant slug, and returns JWT access token along with a UUID refresh token."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Success Example",
                                    value = "{\"success\":true,\"message\":\"Login successful\",\"data\":{\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"refreshToken\":\"439294d1-c277-4b72-b883-fa4a8cdcfc20\",\"tokenType\":\"Bearer\",\"expiresAt\":\"2026-07-10T17:50:00Z\",\"userId\":1,\"username\":\"johndoe\",\"displayName\":\"John Doe\",\"email\":\"johndoe@example.com\",\"tenantId\":1,\"roles\":[\"USER\"],\"permissions\":[\"READ_POST\"]},\"timestamp\":\"2026-07-10T16:50:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid login fields or malformed request structure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Validation Error Example",
                                    value = "{\"success\":false,\"message\":\"Validation failed\",\"data\":{\"success\":false,\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"Validation failed\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{\"usernameOrEmail\":\"Cannot be empty\"}},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Incorrect credentials or login failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Failure Example",
                                    value = "{\"success\":false,\"message\":\"Authentication failed\",\"data\":{\"success\":false,\"errorCode\":\"INVALID_CREDENTIALS\",\"message\":\"Invalid username or password\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "User account is suspended, locked, or tenant is inactive",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Account Locked Example",
                                    value = "{\"success\":false,\"message\":\"Authentication failed\",\"data\":{\"success\":false,\"errorCode\":\"INVALID_CREDENTIALS\",\"message\":\"User account is locked\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tenant slug not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Tenant Not Found Example",
                                    value = "{\"success\":false,\"message\":\"Tenant not found\",\"data\":{\"success\":false,\"errorCode\":\"TENANT_NOT_FOUND\",\"message\":\"Tenant not found\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflicting operations or request state issues",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Conflict Example",
                                    value = "{\"success\":false,\"message\":\"Conflict\",\"data\":{\"success\":false,\"errorCode\":\"CONFLICT\",\"message\":\"Request conflict\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Login Internal Server Error Example",
                                    value = "{\"success\":false,\"message\":\"Internal server error\",\"data\":{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred. Please try again later.\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Login credentials, including username or email, password, and tenant slug selection.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(
                                    name = "Login Request Payload",
                                    value = "{\"usernameOrEmail\":\"johndoe\",\"password\":\"SecurePassword123!\",\"tenantSlug\":\"default\"}"
                            )
                    )
            )
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        LoginResponse response = authenticationService.login(request, servletRequest);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Login successful",
                        response));
    }

    /**
     * Refreshes a user's access token using a valid refresh token.
     *
     * @param request the token refresh request DTO
     * @return response entity containing API response with new access token
     */
    @PostMapping(
            value = "/refresh",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Refresh access token",
            description = "Validates the refresh token and issues a new access token while keeping the same session."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh Success Example",
                                    value = "{\"success\":true,\"message\":\"Token refreshed successfully\",\"data\":{\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"refreshToken\":\"439294d1-c277-4b72-b883-fa4a8cdcfc20\",\"tokenType\":\"Bearer\",\"expiresAt\":\"2026-07-10T17:50:00Z\"},\"timestamp\":\"2026-07-10T16:50:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Blank token parameter or malformed request payload",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh Validation Error Example",
                                    value = "{\"success\":false,\"message\":\"Validation failed\",\"data\":{\"success\":false,\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"Validation failed\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\",\"validationErrors\":{\"refreshToken\":\"Refresh token is required\"}},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid, expired, or revoked refresh token value",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh Token Invalid Example",
                                    value = "{\"success\":false,\"message\":\"Authentication failed\",\"data\":{\"success\":false,\"errorCode\":\"INVALID_CREDENTIALS\",\"message\":\"Invalid refresh token\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "User account has been locked or disabled",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh User Blocked Example",
                                    value = "{\"success\":false,\"message\":\"Forbidden\",\"data\":{\"success\":false,\"errorCode\":\"FORBIDDEN\",\"message\":\"User account is disabled\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Token or entity resources not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh Resource Not Found Example",
                                    value = "{\"success\":false,\"message\":\"Not Found\",\"data\":{\"success\":false,\"errorCode\":\"RESOURCE_NOT_FOUND\",\"message\":\"Entity not found\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Operation conflicts with active transactions",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh Conflict Example",
                                    value = "{\"success\":false,\"message\":\"Conflict\",\"data\":{\"success\":false,\"errorCode\":\"CONFLICT\",\"message\":\"Request conflict\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Refresh Internal Server Error Example",
                                    value = "{\"success\":false,\"message\":\"Internal server error\",\"data\":{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred. Please try again later.\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/refresh\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refresh token details, specifically containing the valid UUID refresh token value.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RefreshTokenRequest.class),
                            examples = @ExampleObject(
                                    name = "Refresh Token Request Payload",
                                    value = "{\"refreshToken\":\"439294d1-c277-4b72-b883-fa4a8cdcfc20\"}"
                            )
                    )
            )
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        RefreshTokenResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Token refreshed successfully",
                        response));
    }

    /**
     * Revokes a refresh token, logging out the user's session.
     *
     * @param request the logout request DTO
     * @return response entity containing API response confirmation
     */
    @PostMapping(
            value = "/logout",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Logout user",
            description = "Revokes the specified refresh token in PostgreSQL to prevent it from being reused."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logout successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Success Example",
                                    value = "{\"success\":true,\"message\":\"Logout successful\",\"data\":null,\"timestamp\":\"2026-07-10T16:50:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Blank token parameter or malformed request payload",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Validation Error Example",
                                    value = "{\"success\":false,\"message\":\"Validation failed\",\"data\":{\"success\":false,\"errorCode\":\"VALIDATION_ERROR\",\"message\":\"Validation failed\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\",\"validationErrors\":{\"refreshToken\":\"Refresh token is required\"}},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired authorization details",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Unauthorized Example",
                                    value = "{\"success\":false,\"message\":\"Unauthorized\",\"data\":{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden from completing logout operation",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Forbidden Example",
                                    value = "{\"success\":false,\"message\":\"Forbidden\",\"data\":{\"success\":false,\"errorCode\":\"FORBIDDEN\",\"message\":\"Access denied\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Specified refresh token not found in the database",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Not Found Example",
                                    value = "{\"success\":false,\"message\":\"Not Found\",\"data\":{\"success\":false,\"errorCode\":\"BAD_REQUEST\",\"message\":\"Refresh token not found\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Token status already updated or modified in background",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Conflict Example",
                                    value = "{\"success\":false,\"message\":\"Conflict\",\"data\":{\"success\":false,\"errorCode\":\"CONFLICT\",\"message\":\"Request conflict\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Logout Internal Server Error Example",
                                    value = "{\"success\":false,\"message\":\"Internal server error\",\"data\":{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred. Please try again later.\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/logout\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Logout details, specifically containing the valid UUID refresh token value to invalidate.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LogoutRequest.class),
                            examples = @ExampleObject(
                                    name = "Logout Request Payload",
                                    value = "{\"refreshToken\":\"439294d1-c277-4b72-b883-fa4a8cdcfc20\"}"
                            )
                    )
            )
            @Valid @RequestBody LogoutRequest request
    ) {
        authenticationService.logout(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Logout successful",
                        null));
    }
}

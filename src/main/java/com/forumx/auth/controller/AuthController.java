package com.forumx.auth.controller;

import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.LogoutRequest;
import com.forumx.auth.dto.request.RefreshTokenRequest;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.dto.request.ResendVerificationRequest;
import com.forumx.auth.dto.request.ForgotPasswordRequest;
import com.forumx.auth.dto.request.ResetPasswordRequest;
import com.forumx.auth.dto.request.GoogleLoginRequest;
import com.forumx.auth.dto.response.CurrentUserResponse;
import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.auth.dto.response.RefreshTokenResponse;
import com.forumx.auth.dto.response.RegisterResponse;
import com.forumx.auth.service.AuthenticationService;
import org.springframework.web.bind.annotation.RequestParam;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
                                     value = "{\"tenantSlug\":\"default\",\"username\":\"visva\",\"email\":\"visva@gmail.com\",\"password\":\"Password@123\",\"confirmPassword\":\"Password@123\"}"
                            )
                    )
            )
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response = authenticationService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        response.getMessage(),
                        response));
    }

    /**
     * Verifies a user's email with the verification token.
     * Note: The email contains a link pointing to the frontend application 
     * (e.g. {@code http://localhost:3000/verify-email?token=<token>}). The frontend 
     * extracts this token and calls this endpoint to finalize the verification.
     *
     * @param token the verification token string
     * @return confirmation of email verification success
     */
    @GetMapping(
            value = "/verify-email",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Verify user email",
            description = "Validates the verification token and flags the user's email address as verified. The token is obtained by the frontend from the email link and sent here."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email verified successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Verify Success Example",
                                    value = "{\"success\":true,\"message\":\"Email verified successfully\",\"data\":null,\"timestamp\":\"2026-07-13T09:00:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid token, expired token, user already verified, or token already used",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Verify Error Example",
                                    value = "{\"success\":false,\"message\":\"Token is invalid\",\"data\":{\"success\":false,\"errorCode\":\"TOKEN_INVALID\",\"message\":\"Token is invalid\",\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/verify-email\"},\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/verify-email\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam("token") String token
    ) {
        authenticationService.verifyEmail(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Email verified successfully",
                        null));
    }

    /**
     * Resends the verification email.
     *
     * @param request the resend details containing the email address
     * @return confirmation of the resend request
     */
    @PostMapping(
            value = "/resend-verification",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Resend email verification token",
            description = "Invalidates any pending verification tokens for the user and generates/sends a new verification link."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Verification email resent successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Resend Success Example",
                                    value = "{\"success\":true,\"message\":\"Verification email has been resent.\",\"data\":null,\"timestamp\":\"2026-07-13T09:00:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "User already verified, or invalid validation parameters",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User email not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        authenticationService.resendVerification(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Verification email has been resent.",
                        null));
    }

    /**
     * Initiates the forgot password workflow.
     *
     * @param request the forgot password details
     * @return confirmation response
     */
    @PostMapping(
            value = "/forgot-password",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Request password reset",
            description = "Triggers a password reset email if the account exists, mitigating user enumeration."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Generic success response",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Forgot Password Success Example",
                                    value = "{\"success\":true,\"message\":\"If an account exists for that email, a password reset link has been sent.\",\"data\":null,\"timestamp\":\"2026-07-13T09:00:00Z\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authenticationService.forgotPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "If an account exists for that email, a password reset link has been sent.",
                        null));
    }

    /**
     * Resets the user password.
     * Note: The email contains a link pointing to the frontend application 
     * (e.g. {@code http://localhost:3000/reset-password?token=<token>}). The frontend 
     * extracts this token, collects the user's new password, and submits them to this endpoint.
     *
     * @param request the reset password details
     * @return confirmation of success
     */
    @PostMapping(
            value = "/reset-password",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Reset password",
            description = "Resets the password using a valid reset token and revokes all active refresh tokens for the user. The token and new password are submitted by the frontend."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Password reset successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Reset Password Success Example",
                                    value = "{\"success\":true,\"message\":\"Password reset successfully\",\"data\":null,\"timestamp\":\"2026-07-13T09:00:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid token, expired token, password reuse, or validation failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Password reset successfully",
                        null));
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
                    description = "User account is suspended, locked, password is not set, or tenant is inactive",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Login Account Locked Example",
                                            value = "{\"success\":false,\"message\":\"Authentication failed\",\"data\":{\"success\":false,\"errorCode\":\"INVALID_CREDENTIALS\",\"message\":\"User account is locked\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                                    ),
                                    @ExampleObject(
                                            name = "Password Not Set Example",
                                            value = "{\"success\":false,\"message\":\"This account currently uses Google Sign-In. Create a password to enable email login.\",\"data\":{\"success\":false,\"errorCode\":\"PASSWORD_NOT_SET\",\"message\":\"This account currently uses Google Sign-In. Create a password to enable email login.\",\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"},\"timestamp\":\"2026-07-10T16:50:00Z\",\"path\":\"/api/v1/auth/login\"}"
                                    )
                            }
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
     * Authenticates a user using Google OAuth ID Token.
     * Automatically links to an existing account if emails match within the resolved tenant.
     *
     * @param request        the Google login payload DTO
     * @param servletRequest the HTTP Servlet Request context
     * @return response entity containing API response with authentication token details
     */
    @PostMapping(
            value = "/google",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Login with Google",
            description = "Validates the Google ID Token and registers or logs in the user, linking accounts if email matches."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Google login successful",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Google Login Success Example",
                                    value = "{\"success\":true,\"message\":\"Login successful\",\"data\":{\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"refreshToken\":\"439294d1-c277-4b72-b883-fa4a8cdcfc20\",\"tokenType\":\"Bearer\",\"expiresAt\":\"2026-07-10T17:50:00Z\",\"userId\":1,\"username\":\"johndoe\",\"displayName\":\"John Doe\",\"email\":\"johndoe@example.com\",\"tenantId\":1,\"roles\":[\"USER\"],\"permissions\":[\"READ_POST\"]},\"timestamp\":\"2026-07-10T16:50:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid ID Token or validation error",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Account hijacked or unverified email",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Google login payload containing the verified ID Token and tenant slug.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GoogleLoginRequest.class),
                            examples = @ExampleObject(
                                    name = "Google Login Request Payload",
                                    value = "{\"idToken\":\"Google_ID_Token_value\",\"tenantSlug\":\"default\"}"
                            )
                    )
            )
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        LoginResponse response = authenticationService.googleLogin(request, servletRequest);
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

    /**
     * Returns the currently authenticated user's profile.
     *
     * @param authentication the Spring Security authentication context
     * @return response entity containing API response with current user details
     */
    @GetMapping(
            value = "/me",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get current authenticated user",
            description = "Returns the profile and authorization details of the currently authenticated user, resolved from the JWT access token."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Current user retrieved successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Current User Success Example",
                                     value = "{\"success\":true,\"message\":\"Current user retrieved successfully\",\"data\":{\"userId\":2,\"username\":\"visva\",\"email\":\"visva@test.com\",\"tenantId\":1,\"tenantSlug\":\"default\",\"active\":true,\"enabled\":true,\"accountNonLocked\":true,\"accountNonExpired\":true,\"credentialsNonExpired\":true,\"roles\":[\"USER\"],\"permissions\":[]},\"timestamp\":\"2026-07-13T09:00:00Z\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired JWT access token",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Current User Unauthorized Example",
                                    value = "{\"success\":false,\"message\":\"Unauthorized\",\"data\":{\"success\":false,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/me\"},\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/me\"}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Authenticated user no longer exists in the database",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "Current User Not Found Example",
                                    value = "{\"success\":false,\"message\":\"Not Found\",\"data\":{\"success\":false,\"errorCode\":\"RESOURCE_NOT_FOUND\",\"message\":\"User not found\",\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/me\"},\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/me\"}"
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
                                    name = "Current User Internal Server Error Example",
                                    value = "{\"success\":false,\"message\":\"Internal server error\",\"data\":{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred. Please try again later.\",\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/me\"},\"timestamp\":\"2026-07-13T09:00:00Z\",\"path\":\"/api/v1/auth/me\"}"
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();
        CurrentUserResponse response = authenticationService.getCurrentUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Current user retrieved successfully",
                        response));
    }

    /**
     * Returns the active workspace memberships and roles for the authenticated user.
     *
     * @param authentication the Spring Security authentication context
     * @return response entity containing list of workspace memberships
     */
    @GetMapping(
            value = "/memberships",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get user workspace memberships",
            description = "Returns all active tenant workspace memberships and roles held by the authenticated user."
    )
    public ResponseEntity<ApiResponse<java.util.List<com.forumx.auth.dto.response.TenantMembershipResponse>>> getUserMemberships(
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();
        java.util.List<com.forumx.auth.dto.response.TenantMembershipResponse> memberships =
                authenticationService.getUserMemberships(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "User memberships retrieved successfully",
                        memberships));
    }
}

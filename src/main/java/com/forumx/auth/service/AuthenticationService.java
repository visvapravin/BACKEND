package com.forumx.auth.service;

import java.util.List;
import java.util.UUID;

import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.LogoutRequest;
import com.forumx.auth.dto.request.RefreshTokenRequest;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.dto.response.CurrentUserResponse;
import com.forumx.auth.dto.response.LoginResponse;
import com.forumx.auth.dto.response.RefreshTokenResponse;
import com.forumx.auth.dto.response.RegisterResponse;
import com.forumx.auth.entity.RefreshToken;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserProfile;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.mapper.AuthMapper;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.security.jwt.JwtTokenProvider;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.auth.dto.request.ResendVerificationRequest;
import com.forumx.auth.verification.entity.VerificationToken;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
import com.forumx.mail.EmailService;
import com.forumx.tenant.resolver.TenantResolver;
import com.forumx.auth.dto.request.ForgotPasswordRequest;
import com.forumx.auth.dto.request.ResetPasswordRequest;
import com.forumx.auth.passwordreset.entity.PasswordResetToken;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.forumx.common.exception.PasswordMismatchException;
import com.forumx.common.exception.PasswordReuseException;
import com.forumx.common.exception.PasswordNotSetException;
import com.forumx.common.exception.UsernameAlreadyExistsException;
import com.forumx.auth.dto.request.GoogleLoginRequest;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service providing authentication and registration core flows for the multi-tenant ForumX backend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final TenantResolver tenantResolver;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final com.forumx.notification.publisher.NotificationPublisher notificationPublisher;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.frontend.verification-path}")
    private String verificationPath;

    @Value("${app.frontend.reset-password-path}")
    private String resetPasswordPath;

    @Value("${app.verification.token-expiration:24h}")
    private Duration tokenExpiration;

    @Value("${app.password-reset.token-expiration:30m}")
    private Duration resetTokenExpiration;

    /**
     * Registers a new User within the specified tenant.
     *
     * @param request the registration details
     * @return the registration response payload
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.debug("Validation details: checking registration request for tenantSlug={}, username={}", request.getTenantSlug(), request.getUsername());
        Tenant tenant = validateTenant(request.getTenantSlug());

        if (userRepository.existsByTenantIdAndUsername(tenant.getId(), request.getUsername())) {
            log.warn("Username already exists in tenant: {}", request.getUsername());
            throw new UsernameAlreadyExistsException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new IllegalArgumentException("Email already exists");
        }

        validatePasswords(request.getPassword(), request.getConfirmPassword());

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = createUser(request, tenant, hashedPassword);
        userRepository.saveAndFlush(user);

        createUserProfile(user);
        assignDefaultRole(user);

        // Generate verification token
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(tokenExpiration))
                .used(false)
                .build();
        verificationTokenRepository.save(verificationToken);

        String verificationUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path(verificationPath)
                .queryParam("token", rawToken)
                .build()
                .toUriString();
        String message = "Registration successful. Please verify your email.";

        try {
            com.forumx.notification.dto.NotificationEvent notificationEvent = new com.forumx.notification.dto.NotificationEvent(
                    java.util.UUID.randomUUID(),
                    tenant.getId(),
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    verificationUrl,
                    "REGISTRATION_VERIFICATION",
                    java.time.Instant.now()
            );
            notificationPublisher.publish(notificationEvent);
        } catch (Exception e) {
            log.error("Failed to publish verification email during registration for username={}", user.getUsername(), e);
            message = "Registration successful, but verification email delivery failed. Please request a new verification email.";
        }

        log.info("Successful registration: username={}, tenant={}", user.getUsername(), tenant.getSlug());
        return buildRegisterResponse(user, message);
    }

    /**
     * Authenticates a user's credentials and issues tokens.
     *
     * @param request        the login credentials
     * @param servletRequest the HTTP servlet request containing client metadata
     * @return the login response payload
     */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        log.debug("Validation details: authenticating usernameOrEmail={}", request.getUsernameOrEmail());

        Long tenantId = tenantResolver.resolveTenantId();
        User checkUser = userRepository.findByTenantIdAndUsernameAndDeletedFalse(tenantId, request.getUsernameOrEmail())
                .or(() -> userRepository.findByTenantIdAndEmailAndDeletedFalse(tenantId, request.getUsernameOrEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (checkUser.getPasswordHash() == null) {
            throw new PasswordNotSetException("This account currently uses Google Sign-In. Create a password to enable email login.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        validateUserStatus(user);
        validateTenant(user.getTenant());

        if (!user.isEmailVerified()) {
            throw new com.forumx.common.exception.EmailNotVerifiedException("Please verify your email before logging in.");
        }

        String accessToken = issueAccessToken(user);
        String refreshToken = issueRefreshToken();

        ClientMetadata metadata = extractClientMetadata(servletRequest);
        persistRefreshToken(user, refreshToken, metadata);

        log.info("Successful login: username={}, tenant={}", user.getUsername(), user.getTenant().getSlug());
        return buildLoginResponse(user, accessToken, refreshToken);
    }

    /**
     * Authenticates a user using a Google ID Token. Links accounts if email matches.
     *
     * @param request        the Google login details
     * @param servletRequest the HTTP servlet request context
     * @return the authentication response containing JWT tokens
     */
    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request, HttpServletRequest servletRequest) {
        log.debug("Validation details: google login for tenantSlug={}", request.getTenantSlug());

        // Step 1: Verify Google ID Token
        GoogleTokenVerifierService.GoogleClaims claims = googleTokenVerifierService.verify(request.getIdToken());
        String googleSub = claims.googleSub();
        String email = claims.email();

        Tenant tenant = validateTenant(request.getTenantSlug());
        User user;

        // Step 2: Find user by google_id globally
        java.util.Optional<User> userByGoogleId = userRepository.findByGoogleIdAndDeletedFalse(googleSub);
        if (userByGoogleId.isPresent()) {
            User existingUser = userByGoogleId.get();
            // Validate that it belongs to the current tenant and email to prevent hijacking / cross-tenant linking
            if (!existingUser.getTenant().getId().equals(tenant.getId()) || !existingUser.getEmail().equals(email)) {
                log.error("SECURITY EVENT: Google ID {} is already linked to user {} (tenant {}) but login requested for tenant {} and email {}",
                        googleSub, existingUser.getEmail(), existingUser.getTenant().getSlug(), tenant.getSlug(), email);
                throw new BadCredentialsException("Google account is already linked to another account");
            }
            user = existingUser;
        } else {
            // Step 3: Find user by tenant and verified email
            java.util.Optional<User> userOpt = userRepository.findByTenantIdAndEmailAndDeletedFalse(tenant.getId(), email);
            if (userOpt.isPresent()) {
                user = userOpt.get();
                if (user.getGoogleId() == null) {
                    // Link Google account
                    user.setGoogleId(googleSub);
                    if (user.getUserProfile() != null && (user.getUserProfile().getAvatarUrl() == null || user.getUserProfile().getAvatarUrl().isEmpty())) {
                        user.getUserProfile().setAvatarUrl(claims.pictureUrl());
                        userProfileRepository.save(user.getUserProfile());
                    }
                    userRepository.save(user);
                } else {
                    // Stored googleId is not null, and doesn't match the sub (since findByGoogleId didn't find it). Reject relinking.
                    log.error("SECURITY EVENT: Google ID mismatch for email {}. Stored Google ID: {}, Verified Google ID: {}",
                            user.getEmail(), user.getGoogleId(), googleSub);
                    throw new BadCredentialsException("Google account mismatch");
                }
            } else {
                // Step 4: Create new User
                // Ensure unique username in the tenant
                String baseUsername = email.split("@")[0];
                String username = baseUsername;
                int count = 1;
                while (userRepository.existsByTenantIdAndUsername(tenant.getId(), username)) {
                    username = baseUsername + count;
                    count++;
                }

                user = User.builder()
                        .tenant(tenant)
                        .username(username)
                        .email(email)
                        .passwordHash(null) // passwordHash = null
                        .emailVerified(true) // emailVerified = true
                        .googleId(googleSub) // googleId = googleSub
                        .status(User.UserStatus.ACTIVE)
                        .enabled(true)
                        .build();
                userRepository.saveAndFlush(user);

                // Create UserProfile
                UserProfile profile = createUserProfile(user);
                user.setUserProfile(profile);
                // Populate profile picture if provided
                if (claims.pictureUrl() != null) {
                    profile.setAvatarUrl(claims.pictureUrl());
                    userProfileRepository.save(profile);
                }

                assignDefaultRole(user);
            }
        }

        validateUserStatus(user);
        validateTenant(user.getTenant());

        String accessToken = issueAccessToken(user);
        String refreshToken = issueRefreshToken();

        ClientMetadata metadata = extractClientMetadata(servletRequest);
        persistRefreshToken(user, refreshToken, metadata);

        log.info("Successful Google login: username={}, tenant={}", user.getUsername(), user.getTenant().getSlug());
        return buildLoginResponse(user, accessToken, refreshToken);
    }

    /**
     * Validates and issues a new access token based on a valid refresh token.
     *
     * @param request the refresh token request
     * @return the token response payload
     */
    @Transactional
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        log.debug("Validation details: refreshing token");
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> {
                    log.warn("Invalid refresh token: not found");
                    return new BadCredentialsException("Invalid refresh token");
                });

        if (refreshToken.isRevoked()) {
            log.warn("Invalid refresh token: already revoked");
            throw new BadCredentialsException("Refresh token is revoked");
        }
        if (refreshToken.isExpired()) {
            log.warn("Invalid refresh token: expired");
            throw new BadCredentialsException("Refresh token has expired");
        }

        User user = refreshToken.getUser();
        validateUserStatus(user);
        validateTenant(user.getTenant());

        String newAccessToken = issueAccessToken(user);

        log.info("Successful token refresh: username={}", user.getUsername());
        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresAt(jwtTokenProvider.extractExpiration(newAccessToken).toInstant())
                .build();
    }

    /**
     * Revokes a refresh token, logging out the user's session.
     *
     * @param request the logout request
     */
    @Transactional
    public void logout(LogoutRequest request) {
        log.debug("Validation details: logging out refresh token");
        revokeRefreshToken(request.getRefreshToken());
        log.info("Successful logout");
    }

    /**
     * Retrieves the current authenticated user's profile.
     *
     * @param userId the authenticated user's ID
     * @return the current user response payload
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        log.debug("Retrieving current user profile for userId={}", userId);

        User user = userRepository.findByIdWithFullProfile(userId)
                .orElseThrow(() -> {
                    log.warn("Authenticated user not found: userId={}", userId);
                    return new IllegalArgumentException("User not found");
                });

        CustomUserDetails userDetails = new CustomUserDetails(user);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .toList();

        List<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .toList();

        return CurrentUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())

                .tenantId(user.getTenant().getId())
                .tenantSlug(user.getTenant().getSlug())
                .active(user.getStatus() == User.UserStatus.ACTIVE)
                .enabled(user.isEnabled())
                .accountNonLocked(!user.isAccountLocked())
                .accountNonExpired(!user.isAccountExpired())
                .credentialsNonExpired(!user.isCredentialsExpired())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    /**
     * Validates the tenant status by slug during registration.
     *
     * @param tenantSlug the tenant identifier slug
     * @return the found and active Tenant
     */
    private Tenant validateTenant(String tenantSlug) {
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        if (!tenant.isActive()) {
            log.warn("Tenant disabled: {}", tenantSlug);
            throw new IllegalArgumentException("Tenant is inactive");
        }
        return tenant;
    }

    /**
     * Validates the tenant status during login.
     *
     * @param tenant the Tenant entity
     */
    private void validateTenant(Tenant tenant) {
        if (!tenant.isActive()) {
            log.warn("Tenant disabled: {}", tenant.getSlug());
            throw new DisabledException("Tenant is inactive");
        }
    }

    /**
     * Asserts that the password and its confirmation match.
     *
     * @param password        the chosen password
     * @param confirmPassword the confirmed password
     */
    private void validatePasswords(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            log.debug("Validation details: password mismatch");
            throw new IllegalArgumentException("Passwords do not match");
        }
    }

    /**
     * Asserts the user account status is enabled and not locked.
     *
     * @param user the User entity
     */
    private void validateUserStatus(User user) {
        if (!user.isEnabled()) {
            log.warn("User account is disabled: {}", user.getUsername());
            throw new DisabledException("User account is disabled");
        }
        if (user.isAccountLocked()) {
            log.warn("User account is locked: {}", user.getUsername());
            throw new LockedException("User account is locked");
        }
    }

    /**
     * Creates and configures the User entity.
     *
     * @param request        the registration request
     * @param tenant         the tenant reference
     * @param hashedPassword the hashed password
     * @return the configured User entity
     */
    private User createUser(RegisterRequest request, Tenant tenant, String hashedPassword) {
        User user = authMapper.toUser(request);
        user.setTenant(tenant);
        user.setPasswordHash(hashedPassword);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEnabled(true);
        user.setEmailVerified(false);
        return user;
    }

    private UserProfile createUserProfile(User user) {
        UserProfile profile = authMapper.toUserProfile(user);
        return userProfileRepository.save(profile);
    }

    /**
     * Assigns the default ROLE_USER to the specified user.
     *
     * @param user the target User
     */
    private void assignDefaultRole(User user) {
        Role defaultRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseThrow(() -> new IllegalArgumentException("Default Role USER not found"));
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(defaultRole)
                .active(true)
                .build();
        userRoleRepository.save(userRole);
    }

    /**
     * Generates a new access token for the user.
     *
     * @param user the User entity
     * @return access token JWT string
     */
    private String issueAccessToken(User user) {
        UserDetails userDetails = new CustomUserDetails(user);
        return jwtTokenProvider.generateAccessToken(userDetails);
    }

    /**
     * Issues a random UUID-based refresh token.
     *
     * @return UUID refresh token string
     */
    private String issueRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Persists the refresh token to the database.
     *
     * @param user        the User entity
     * @param tokenString the refresh token string
     * @param metadata    the client HTTP details
     */
    private void persistRefreshToken(User user, String tokenString, ClientMetadata metadata) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenString)
                .ipAddress(metadata.ipAddress())
                .userAgent(metadata.userAgent())
                .deviceId(metadata.deviceId())
                .deviceName(metadata.deviceName())
                .expiresAt(jwtTokenProvider.calculateRefreshTokenExpiry())
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Extract client details from the HTTP request context.
     *
     * @param request the HTTP request
     * @return client details metadata holder
     */
    private ClientMetadata extractClientMetadata(HttpServletRequest request) {
        return new ClientMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("X-Device-Id"),
                request.getHeader("X-Device-Name")
        );
    }

    /**
     * Revokes a refresh token by token string.
     *
     * @param tokenString the refresh token string
     */
    private void revokeRefreshToken(String tokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> {
                    log.warn("Invalid refresh token logout attempt");
                    return new IllegalArgumentException("Refresh token not found");
                });
        if (refreshToken.isRevoked()) {
            log.info("Refresh token already revoked");
            return;
        }
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Builds the LoginResponse payload from the user and token details.
     *
     * @param user         the authenticated user
     * @param accessToken  the access token JWT
     * @param refreshToken the refresh token UUID
     * @return populated LoginResponse DTO
     */
    private LoginResponse buildLoginResponse(User user, String accessToken, String refreshToken) {
        LoginResponse response = authMapper.toLoginResponse(user);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresAt(jwtTokenProvider.extractExpiration(accessToken).toInstant());

        CustomUserDetails userDetails = new CustomUserDetails(user);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .toList();

        List<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .toList();

        response.setRoles(roles);
        response.setPermissions(permissions);
        return response;
    }

    /**
     * Builds the RegisterResponse payload.
     *
     * @param user    the registered user
     * @param message registration success message
     * @return populated RegisterResponse DTO
     */
    private RegisterResponse buildRegisterResponse(User user, String message) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .tenantId(user.getTenant().getId())
                .message(message)
                .build();
    }

    /**
     * Verifies a user's email address using the provided raw token.
     *
     * @param rawToken the raw token received in the verification link
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new com.forumx.common.exception.InvalidTokenException("Token is invalid");
        }

        String tokenHash = hashToken(rawToken);
        VerificationToken verificationToken = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new com.forumx.common.exception.InvalidTokenException("Token is invalid"));

        if (verificationToken.isUsed()) {
            throw new com.forumx.common.exception.TokenAlreadyUsedException("Verification token has already been used");
        }

        if (verificationToken.isExpired()) {
            throw new com.forumx.common.exception.ExpiredTokenException("Verification token has expired");
        }

        User user = verificationToken.getUser();
        if (user.isEmailVerified()) {
            throw new com.forumx.common.exception.AlreadyVerifiedException("User is already verified");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationToken.setUsedAt(Instant.now());
        verificationTokenRepository.save(verificationToken);

        log.info("Email verified successfully for user username={}", user.getUsername());
    }

    /**
     * Invalidates prior unused tokens and generates/sends a new verification email.
     *
     * @param request the resend request containing the user's email
     */
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        Long tenantId = tenantResolver.resolveTenantId();
        User user = userRepository.findByTenantIdAndEmailAndDeletedFalse(tenantId, request.getEmail())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new com.forumx.common.exception.AlreadyVerifiedException("User is already verified");
        }

        // Invalidate old verification tokens
        List<VerificationToken> unusedTokens = verificationTokenRepository.findAllByUserAndUsedFalse(user);
        for (VerificationToken oldToken : unusedTokens) {
            oldToken.setUsed(true);
            oldToken.setUsedAt(Instant.now());
        }
        verificationTokenRepository.saveAll(unusedTokens);

        // Generate new token
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        VerificationToken newVerificationToken = VerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(tokenExpiration))
                .used(false)
                .build();
        verificationTokenRepository.save(newVerificationToken);

        String verificationUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path(verificationPath)
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        try {
            com.forumx.notification.dto.NotificationEvent notificationEvent = new com.forumx.notification.dto.NotificationEvent(
                    java.util.UUID.randomUUID(),
                    tenantId,
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    verificationUrl,
                    "REGISTRATION_VERIFICATION",
                    java.time.Instant.now()
            );
            notificationPublisher.publish(notificationEvent);
        } catch (Exception e) {
            log.error("Failed to resend verification email for username={}", user.getUsername(), e);
            throw new RuntimeException("Email delivery failed", e);
        }
        log.info("Resent verification email successfully to user {}", user.getUsername());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 digest algorithm not available", e);
        }
    }

    /*
     * Email links intentionally point to the frontend application.
     *
     * The frontend is responsible for:
     *  - reading the token
     *  - collecting user input
     *  - invoking backend REST APIs.
     *
     * The backend remains a stateless REST service.
     */
    /**
     * Initiates the forgot password workflow by issuing a secure reset token
     * and sending an email. Does not reveal whether the email exists.
     *
     * @param request the forgot password request containing the user's email
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Long tenantId = tenantResolver.resolveTenantId();
        java.util.Optional<User> userOpt = userRepository.findByTenantIdAndEmailAndDeletedFalse(tenantId, request.getEmail());

        if (userOpt.isEmpty()) {
            // Mitigate user enumeration via dummy delay / fake hashing
            passwordEncoder.encode("dummyForUserEnumerationSafety");
            log.info("Forgot password requested for non-existing email: {}", request.getEmail());
            return;
        }

        User user = userOpt.get();

        // Invalidate prior unused reset tokens
        List<PasswordResetToken> activeTokens = passwordResetTokenRepository.findAllByUserAndUsedFalse(user);
        for (PasswordResetToken token : activeTokens) {
            token.setUsed(true);
            token.setUsedAt(Instant.now());
        }
        passwordResetTokenRepository.saveAll(activeTokens);

        // Generate reset token
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(resetTokenExpiration))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Build reset URL using configured base URL and Spring's UriComponentsBuilder
        String resetUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path(resetPasswordPath)
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetUrl);
        } catch (Exception e) {
            // SMTP failures must not roll back the database transaction
            log.error("Failed to send password reset email for user {}", user.getUsername(), e);
        }

        log.info("Password reset token generated and email dispatched for user {}", user.getUsername());
    }

    /**
     * Resets a user's password using the provided reset token.
     * Revokes all refresh tokens upon successful reset.
     *
     * @param request the reset password details
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        String tokenHash = hashToken(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new com.forumx.common.exception.InvalidTokenException("Token is invalid"));

        if (resetToken.isUsed()) {
            throw new com.forumx.common.exception.TokenAlreadyUsedException("Password reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new com.forumx.common.exception.ExpiredTokenException("Password reset token has expired");
        }

        User user = resetToken.getUser();

        // Reject password reuse
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new PasswordReuseException("Cannot reset password to the current password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        // Revoke all active refresh tokens for the user
        List<RefreshToken> activeRefreshTokens = refreshTokenRepository.findAllByUser(user);
        for (RefreshToken rfToken : activeRefreshTokens) {
            rfToken.revoke();
        }
        refreshTokenRepository.saveAll(activeRefreshTokens);

        log.info("Password reset successfully and refresh tokens revoked for user {}", user.getUsername());
        
        // Note: Expired and used verification/password reset tokens are periodically cleaned up by TokenCleanupService.
    }

    /**
     * Record to hold extracted HTTP client metadata.
     */
    private record ClientMetadata(
            String ipAddress,
            String userAgent,
            String deviceId,
            String deviceName
    ) {
    }
}

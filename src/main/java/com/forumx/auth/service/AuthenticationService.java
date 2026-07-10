package com.forumx.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forumx.auth.dto.request.LoginRequest;
import com.forumx.auth.dto.request.LogoutRequest;
import com.forumx.auth.dto.request.RefreshTokenRequest;
import com.forumx.auth.dto.request.RegisterRequest;
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
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByTenantIdAndEmail(tenant.getId(), request.getEmail())) {
            log.warn("Email already exists in tenant: {}", request.getEmail());
            throw new IllegalArgumentException("Email already exists");
        }

        validatePasswords(request.getPassword(), request.getConfirmPassword());

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = createUser(request, tenant, hashedPassword);
        userRepository.saveAndFlush(user);

        createUserProfile(request, user);
        assignDefaultRole(user);

        log.info("Successful registration: username={}, tenant={}", user.getUsername(), tenant.getSlug());
        return buildRegisterResponse(user, "User registered successfully");
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

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        validateUserStatus(user);
        validateTenant(user.getTenant());

        String accessToken = issueAccessToken(user);
        String refreshToken = issueRefreshToken();

        ClientMetadata metadata = extractClientMetadata(servletRequest);
        persistRefreshToken(user, refreshToken, metadata);

        log.info("Successful login: username={}, tenant={}", user.getUsername(), user.getTenant().getSlug());
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
        user.setPhoneNumber(request.getPhoneNumber());
        return user;
    }

    /**
     * Maps and saves the user profile details.
     *
     * @param request the registration request
     * @param user    the parent User entity
     * @return the saved UserProfile entity
     */
    private UserProfile createUserProfile(RegisterRequest request, User user) {
        UserProfile profile = authMapper.toUserProfile(user);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDisplayName(request.getDisplayName());
        profile.setCompany(request.getCompany());
        profile.setDepartment(request.getDepartment());
        profile.setJobTitle(request.getJobTitle());
        profile.setTimezone(request.getTimezone());
        profile.setLocale(request.getLocale());
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

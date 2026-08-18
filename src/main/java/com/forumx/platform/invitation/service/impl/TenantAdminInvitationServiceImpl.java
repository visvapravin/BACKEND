package com.forumx.platform.invitation.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserProfile;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AccountScopeValidator;
import com.forumx.common.exception.InvitationAlreadyAcceptedException;
import com.forumx.common.exception.InvitationAlreadyPendingException;
import com.forumx.common.exception.InvitationAlreadyRevokedException;
import com.forumx.common.exception.InvitationNotFoundException;
import com.forumx.common.exception.PasswordMismatchException;
import com.forumx.common.exception.UsernameAlreadyExistsException;
import com.forumx.notification.dto.NotificationEvent;
import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.publisher.NotificationPublisher;
import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationResponse;
import com.forumx.platform.invitation.dto.CreateTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.TenantAdminInvitationResponse;
import com.forumx.platform.invitation.dto.ValidateTenantAdminInvitationResponse;
import com.forumx.platform.invitation.entity.TenantAdminInvitation;
import com.forumx.platform.invitation.repository.TenantAdminInvitationRepository;
import com.forumx.platform.invitation.service.TenantAdminInvitationService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminInvitationServiceImpl implements TenantAdminInvitationService {

    private static final Duration DEFAULT_EXPIRATION = Duration.ofDays(7);

    private final TenantAdminInvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationFacade authenticationFacade;
    private final AccountScopeValidator accountScopeValidator;
    private final NotificationPublisher notificationPublisher;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    // -- Create Invitation --

    @Override
    @Transactional
    public TenantAdminInvitationResponse createInvitation(Long tenantId, CreateTenantAdminInvitationRequest request) {
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (details == null) {
            throw new IllegalStateException("Authentication context required");
        }

        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + tenantId));

        User inviter = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Inviter user not found with ID: " + details.getUserId()));

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (invitationRepository.existsByTenant_IdAndEmailAndStatusAndDeletedFalse(tenantId, normalizedEmail, InvitationStatus.PENDING)) {
            throw new InvitationAlreadyPendingException("A pending tenant admin invitation already exists for email: " + normalizedEmail);
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        TenantAdminInvitation invitation = TenantAdminInvitation.builder()
                .tenant(tenant)
                .email(normalizedEmail)
                .role(RoleType.TENANT_ADMIN)
                .tokenHash(tokenHash)
                .invitedBy(inviter)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(DEFAULT_EXPIRATION))
                .build();

        TenantAdminInvitation saved = invitationRepository.save(invitation);
        log.info("TENANT_ADMIN_INVITATION_CREATED tenantId={} inviterId={} email={} invitationId={}",
                tenantId, inviter.getId(), normalizedEmail, saved.getId());

        dispatchInvitationEmail(tenant, inviter, normalizedEmail, rawToken);

        return mapToResponse(saved);
    }

    // -- Validate Invitation --

    @Override
    @Transactional(readOnly = true)
    public ValidateTenantAdminInvitationResponse validateInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return ValidateTenantAdminInvitationResponse.builder().valid(false).build();
        }

        String tokenHash = hashToken(rawToken);
        TenantAdminInvitation invitation = invitationRepository.findByTokenHashAndDeletedFalse(tokenHash)
                .orElse(null);

        if (invitation == null || invitation.getStatus() != InvitationStatus.PENDING || invitation.isExpired()) {
            return ValidateTenantAdminInvitationResponse.builder().valid(false).build();
        }

        return ValidateTenantAdminInvitationResponse.builder()
                .tenantName(invitation.getTenant().getName())
                .tenantSlug(invitation.getTenant().getSlug())
                .email(invitation.getEmail())
                .expiresAt(invitation.getExpiresAt())
                .valid(true)
                .build();
    }

    // -- Accept Invitation --

    @Override
    @Transactional
    public AcceptTenantAdminInvitationResponse acceptInvitation(
            AcceptTenantAdminInvitationRequest request,
            HttpServletRequest servletRequest) {

        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        String tokenHash = hashToken(request.getToken());
        TenantAdminInvitation invitation = invitationRepository.findByTokenHashAndDeletedFalse(tokenHash)
                .orElseThrow(() -> new InvitationNotFoundException("Invalid or missing invitation token"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException("Invitation has already been accepted");
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvitationAlreadyRevokedException("Invitation has been revoked");
        }
        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new IllegalArgumentException("Invitation token has expired");
        }

        Tenant tenant = invitation.getTenant();
        RoleType roleType = RoleType.TENANT_ADMIN;

        accountScopeValidator.validate(roleType, tenant);

        if (userRepository.existsByTenantIdAndUsername(tenant.getId(), request.getUsername().trim())) {
            throw new UsernameAlreadyExistsException("Username already exists in tenant");
        }
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role tenantAdminRole = roleRepository.findByRoleName(roleType)
                .orElseThrow(() -> new IllegalStateException(
                        "TENANT_ADMIN role not found in database. Ensure Flyway migrations have run."));

        User user = User.builder()
                .tenant(tenant)
                .username(request.getUsername().trim())
                .email(invitation.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .user(savedUser)
                .build();
        userProfileRepository.save(profile);

        UserRole userRole = UserRole.builder()
                .user(savedUser)
                .role(tenantAdminRole)
                .active(true)
                .build();
        userRoleRepository.save(userRole);
        savedUser.getUserRoles().add(userRole);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("TENANT_ADMIN_INVITATION_ACCEPTED userId={} tenantSlug={} email={}",
                savedUser.getId(), tenant.getSlug(), invitation.getEmail());

        return AcceptTenantAdminInvitationResponse.builder()
                .message("Invitation accepted successfully. Please log in with your credentials.")
                .loginRequired(true)
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .tenantSlug(tenant.getSlug())
                .nextAction("PROCEED_TO_LOGIN")
                .build();
    }

    // -- Private helpers --

    private void dispatchInvitationEmail(Tenant tenant, User inviter, String email, String rawToken) {
        try {
            String invitationUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                    .path("/accept-admin-invitation")
                    .queryParam("token", rawToken)
                    .build()
                    .toUriString();

            NotificationEvent event = new NotificationEvent(
                    UUID.randomUUID(),
                    tenant.getId(),
                    inviter.getId(),
                    email,
                    "You have been invited as a Tenant Administrator",
                    invitationUrl,
                    EmailTemplateType.MODERATOR_INVITATION.name(),
                    Instant.now()
            );
            notificationPublisher.publish(event);
            log.info("Published TENANT_ADMIN_INVITATION email event for email={} under tenant={}",
                    email, tenant.getSlug());
        } catch (Exception e) {
            log.error("Failed to publish tenant admin invitation email for email={}: {}",
                    email, e.getMessage());
        }
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
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private TenantAdminInvitationResponse mapToResponse(TenantAdminInvitation invitation) {
        return TenantAdminInvitationResponse.builder()
                .id(invitation.getId())
                .tenantId(invitation.getTenant().getId())
                .tenantName(invitation.getTenant().getName())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .status(invitation.getStatus())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}

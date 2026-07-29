package com.forumx.auth.invitation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserProfile;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.service.AccountScopeValidator;
import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.dto.response.AcceptInvitationResponse;
import com.forumx.auth.invitation.dto.response.InvitationResponse;
import com.forumx.auth.invitation.dto.response.InvitationValidationResponse;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.common.exception.ExpiredTokenException;
import com.forumx.common.exception.InvalidTokenException;
import com.forumx.common.exception.InvitationAlreadyAcceptedException;
import com.forumx.common.exception.InvitationAlreadyPendingException;
import com.forumx.common.exception.InvitationAlreadyRevokedException;
import com.forumx.common.exception.InvitationNotFoundException;
import com.forumx.common.exception.PasswordMismatchException;
import com.forumx.common.exception.UsernameAlreadyExistsException;
import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.publisher.NotificationPublisher;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final ModeratorInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final TenantRepository tenantRepository;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPublisher notificationPublisher;
    private final AccountScopeValidator accountScopeValidator;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.invitation.expiration:7d}")
    private Duration invitationExpiration;

    /**
     * Creates a new Moderator invitation for the specified email within the active tenant.
     * Accessible by TENANT_ADMIN and PLATFORM_ADMIN.
     */
    @Transactional
    public InvitationResponse createModeratorInvitation(CreateInvitationRequest request) {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails userDetails = authenticationFacade.getCurrentUserDetails();

        if (tenantId == null || userDetails == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }

        if (!tenantId.equals(userDetails.getTenantId())) {
            throw new AccessDeniedException("Inviter does not belong to the active tenant context");
        }

        User inviter = userRepository.findByIdAndDeletedFalse(userDetails.getUserId())
                .orElseThrow(() -> new AccessDeniedException("Inviter user not found"));

        if (!inviter.getTenant().getId().equals(tenantId)) {
            throw new AccessDeniedException("Inviter does not belong to the target tenant");
        }

        Tenant tenant = inviter.getTenant();
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Check if email already belongs to an existing registered user
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Invitation rejected: Account already exists for email {}", normalizedEmail);
            throw new IllegalArgumentException("Account already exists for this email. Tenant administrator must use staff management role assignment.");
        }

        // Check active pending invitations for same email & tenant
        Optional<ModeratorInvitation> existingPendingOpt = invitationRepository.findByTenant_IdAndEmailAndStatusAndDeletedFalse(
                tenantId, normalizedEmail, InvitationStatus.PENDING);

        if (existingPendingOpt.isPresent()) {
            ModeratorInvitation existing = existingPendingOpt.get();
            if (existing.isExpired()) {
                existing.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(existing);
            } else {
                throw new InvitationAlreadyPendingException("An active invitation already exists for this email address.");
            }
        }

        // Generate cryptographically secure token
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(invitationExpiration != null ? invitationExpiration : Duration.ofDays(7));

        ModeratorInvitation invitation = ModeratorInvitation.builder()
                .tenant(tenant)
                .email(normalizedEmail)
                .role(RoleType.MODERATOR)
                .tokenHash(tokenHash)
                .invitedBy(inviter)
                .status(InvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        ModeratorInvitation saved = invitationRepository.save(invitation);

        // Build invitation URL for email link
        String invitationUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path("/accept-invitation")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        try {
            com.forumx.notification.dto.NotificationEvent notificationEvent = new com.forumx.notification.dto.NotificationEvent(
                    java.util.UUID.randomUUID(),
                    tenant.getId(),
                    inviter.getId(),
                    normalizedEmail,
                    "Invited Moderator",
                    invitationUrl,
                    EmailTemplateType.MODERATOR_INVITATION.name(),
                    Instant.now()
            );
            notificationPublisher.publish(notificationEvent);
            log.info("Published MODERATOR_INVITATION email event for email={} under tenant={}", normalizedEmail, tenant.getSlug());
        } catch (Exception e) {
            log.error("Failed to publish invitation email for email={}", normalizedEmail, e);
        }

        return toResponse(saved);
    }

    /**
     * Rotates the credentials for an existing invitation and sends it through the
     * same notification pipeline used when an invitation is first created.
     *
     * <p>The tenant and actor come exclusively from the authenticated request
     * context.  Keeping this lifecycle operation here prevents staff management
     * from duplicating the Phase B token generation and email dispatch logic.</p>
     */
    @Transactional
    public ModeratorInvitation resendModeratorInvitation(Long invitationId) {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails userDetails = authenticationFacade.getCurrentUserDetails();

        if (tenantId == null || userDetails == null || !tenantId.equals(userDetails.getTenantId())) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }

        ModeratorInvitation invitation = invitationRepository
                .findByIdAndTenant_IdAndDeletedFalse(invitationId, tenantId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found: " + invitationId));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException("Cannot resend an accepted invitation");
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvitationAlreadyRevokedException("Cannot resend a revoked invitation");
        }

        String rawToken = generateRawToken();
        invitation.setTokenHash(hashToken(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(
                invitationExpiration != null ? invitationExpiration : Duration.ofDays(7)));
        invitation.setAcceptedAt(null);
        invitation.setRevokedAt(null);

        ModeratorInvitation saved = invitationRepository.save(invitation);
        String invitationUrl = UriComponentsBuilder.fromHttpUrl(frontendBaseUrl)
                .path("/accept-invitation")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        try {
            notificationPublisher.publish(new com.forumx.notification.dto.NotificationEvent(
                    java.util.UUID.randomUUID(),
                    tenantId,
                    userDetails.getUserId(),
                    saved.getEmail(),
                    "Invited Moderator",
                    invitationUrl,
                    EmailTemplateType.MODERATOR_INVITATION.name(),
                    Instant.now()
            ));
            log.info("MODERATOR_INVITATION_RESENT tenantId={} actorUserId={} invitationId={} email={}",
                    tenantId, userDetails.getUserId(), invitationId, saved.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish resend invitation email for invitationId={}: {}",
                    invitationId, e.getMessage());
        }

        return saved;
    }

    /**
     * Validates a raw invitation token. Public endpoint.
     */
    @Transactional
    public InvitationValidationResponse validateInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Invitation token is required");
        }

        String tokenHash = hashToken(rawToken);
        ModeratorInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation token is invalid or not found"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException("Invitation has already been accepted");
        }

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvalidTokenException("Invitation has been revoked");
        }

        if (invitation.isExpired() || invitation.getStatus() == InvitationStatus.EXPIRED) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(invitation);
            }
            throw new ExpiredTokenException("Invitation token has expired");
        }

        return InvitationValidationResponse.builder()
                .valid(true)
                .email(invitation.getEmail())
                .tenantName(invitation.getTenant().getName())
                .role(invitation.getRole())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    /**
     * Accepts a moderator invitation, creates the user account in the invitation tenant,
     * assigns MODERATOR role, and marks the invitation ACCEPTED.
     */
    @Transactional
    public AcceptInvitationResponse acceptInvitation(AcceptInvitationRequest request) {
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        String tokenHash = hashToken(request.getToken());
        ModeratorInvitation invitation = invitationRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation token is invalid or not found"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException("Invitation has already been accepted");
        }

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvalidTokenException("Invitation has been revoked");
        }

        if (invitation.isExpired() || invitation.getStatus() == InvitationStatus.EXPIRED) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(invitation);
            }
            throw new ExpiredTokenException("Invitation token has expired");
        }

        Tenant tenant = invitation.getTenant();
        String email = invitation.getEmail();

        // Enforce the account-scope invariant before any tenant-dependent
        // lookup or persistence. The invitation is the trusted tenant source.
        accountScopeValidator.validate(RoleType.MODERATOR, tenant);

        // Check username uniqueness in tenant
        if (userRepository.existsByTenantIdAndUsername(tenant.getId(), request.getUsername())) {
            throw new UsernameAlreadyExistsException("Username already exists in tenant");
        }

        // Check email uniqueness globally
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Account already exists for this email address");
        }

        // Create new User
        User user = User.builder()
                .tenant(tenant)
                .username(request.getUsername())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .emailVerified(true)
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        // Create UserProfile
        UserProfile profile = UserProfile.builder()
                .user(savedUser)
                .build();
        userProfileRepository.save(profile);

        // Assign MODERATOR role
        Role moderatorRole = roleRepository.findByRoleName(RoleType.MODERATOR)
                .orElseThrow(() -> new IllegalStateException("MODERATOR role not found"));

        UserRole userRole = UserRole.builder()
                .user(savedUser)
                .role(moderatorRole)
                .active(true)
                .build();
        userRoleRepository.save(userRole);

        // Mark invitation ACCEPTED
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("Invitation accepted successfully: email={}, username={}, tenant={}", email, savedUser.getUsername(), tenant.getSlug());

        return AcceptInvitationResponse.builder()
                .message("Invitation accepted successfully. Please log in with your credentials.")
                .loginRequired(true)
                .build();
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
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private InvitationResponse toResponse(ModeratorInvitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .tenantId(invitation.getTenant().getId())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .status(invitation.getStatus())
                .invitedByUsername(invitation.getInvitedBy() != null ? invitation.getInvitedBy().getUsername() : null)
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}

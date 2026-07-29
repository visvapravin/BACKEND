package com.forumx.auth.staff.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import com.forumx.auth.invitation.repository.ModeratorInvitationRepository;
import com.forumx.auth.invitation.service.InvitationService;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.staff.dto.response.InvitationListResponse;
import com.forumx.auth.staff.dto.response.ModeratorDetailResponse;
import com.forumx.auth.staff.dto.response.ModeratorSummaryResponse;
import com.forumx.auth.staff.service.TenantStaffService;
import com.forumx.common.exception.InvitationAlreadyAcceptedException;
import com.forumx.common.exception.InvitationAlreadyRevokedException;
import com.forumx.common.exception.InvitationNotFoundException;
import com.forumx.common.exception.ModeratorAlreadyDisabledException;
import com.forumx.common.exception.ModeratorAlreadyEnabledException;
import com.forumx.common.exception.ModeratorNotFoundException;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.tenant.resolver.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

/**
 * Implementation of {@link TenantStaffService}.
 *
 * <h2>Architectural decisions</h2>
 * <ul>
 *   <li><strong>Role authoritativeness:</strong> {@code user.getUserRoles()} is never used as
 *       the authoritative source. All role membership is determined via {@link UserRoleRepository}
 *       queries to avoid Hibernate L1 cache staleness.</li>
 *   <li><strong>Tenant isolation:</strong> Every operation resolves tenantId from the authenticated
 *       context (never from request params). Cross-tenant access returns NOT_FOUND semantics.</li>
 *   <li><strong>Account disable:</strong> Uses {@code user.deactivate()} → INACTIVE + enabled=false.
 *       {@code JwtAuthenticationFilter} re-loads UserDetails on every request, so disable is
 *       immediately effective for subsequent requests using any existing access JWT.</li>
 *   <li><strong>Invitation lifecycle:</strong> credential rotation and email dispatch are delegated
 *       to {@code InvitationService}, the Phase B owner of token and notification behavior.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantStaffServiceImpl implements TenantStaffService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ModeratorInvitationRepository invitationRepository;
    private final InvitationService invitationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ChatSessionService chatSessionService;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;

    // ── Moderator List ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ModeratorSummaryResponse> listModerators(Pageable pageable) {
        Long tenantId = resolvedTenantId();

        // Step 1: fetch IDs at DB level — avoids incorrect Hibernate pagination on JOIN FETCH
        Page<Long> userIdPage = userRoleRepository.findUserIdsWithActiveRoleInTenant(
                tenantId, RoleType.MODERATOR, pageable);

        if (userIdPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // Step 2: load full user entities for the current page of IDs
        List<User> users = userRepository.findAllById(userIdPage.getContent());

        List<ModeratorSummaryResponse> content = users.stream()
                .map(ModeratorSummaryResponse::from)
                .toList();

        return new PageImpl<>(content, pageable, userIdPage.getTotalElements());
    }

    // ── Moderator Detail ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ModeratorDetailResponse getModeratorDetail(Long userId) {
        Long tenantId = resolvedTenantId();
        User user = requireModeratorInTenant(userId, tenantId);
        return ModeratorDetailResponse.from(user);
    }

    // ── Invitation List ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<InvitationListResponse> listInvitations(InvitationStatus statusFilter, Pageable pageable) {
        Long tenantId = resolvedTenantId();

        Page<ModeratorInvitation> page;
        if (statusFilter != null) {
            page = invitationRepository.findByTenant_IdAndStatusAndDeletedFalse(tenantId, statusFilter, pageable);
        } else {
            page = invitationRepository.findByTenant_IdAndDeletedFalse(tenantId, pageable);
        }

        return page.map(InvitationListResponse::from);
    }

    // ── Resend Invitation ───────────────────────────────────────────────

    @Override
    @Transactional
    public InvitationListResponse resendInvitation(Long invitationId) {
        return InvitationListResponse.from(invitationService.resendModeratorInvitation(invitationId));
    }

    // ── Revoke Invitation ───────────────────────────────────────────────

    @Override
    @Transactional
    public void revokeInvitation(Long invitationId) {
        Long tenantId = resolvedTenantId();
        CustomUserDetails actor = currentUserDetails();

        ModeratorInvitation invitation = invitationRepository
                .findByIdAndTenant_IdAndDeletedFalse(invitationId, tenantId)
                .orElseThrow(() -> new InvitationNotFoundException(
                        "Invitation not found: " + invitationId));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new InvitationAlreadyAcceptedException(
                    "Cannot revoke an accepted invitation");
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new InvitationAlreadyRevokedException(
                    "Invitation is already revoked");
        }

        // Domain rule: both PENDING (including expired ones still showing PENDING) and EXPIRED
        // can be revoked — domain intent is to prevent the token from being accepted.
        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRevokedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("MODERATOR_INVITATION_REVOKED tenantId={} actorUserId={} invitationId={} email={}",
                tenantId, actor.getUserId(), invitationId, invitation.getEmail());
    }

    // ── Disable Moderator ───────────────────────────────────────────────

    @Override
    @Transactional
    public void disableModerator(Long userId) {
        Long tenantId = resolvedTenantId();
        CustomUserDetails actor = currentUserDetails();

        User moderator = requireModeratorInTenant(userId, tenantId);

        if (!moderator.isEnabled()) {
            throw new ModeratorAlreadyDisabledException(
                    "Moderator account is already disabled");
        }

        // 1. Deactivate account — status=INACTIVE, enabled=false
        // JwtAuthenticationFilter re-loads UserDetails per request, so disabled state
        // takes effect immediately on the next authenticated request using any existing JWT.
        moderator.deactivate();
        userRepository.save(moderator);

        // 2. Revoke all refresh tokens — preserves records for audit history
        refreshTokenRepository.revokeAllByUserId(userId);

        // 3. Terminate active support-room participations via support domain service
        int terminated = chatSessionService.terminateActiveParticipations(userId);

        log.info("MODERATOR_DISABLED tenantId={} actorUserId={} targetUserId={} participationsTerminated={}",
                tenantId, actor.getUserId(), userId, terminated);
    }

    // ── Enable Moderator ────────────────────────────────────────────────

    @Override
    @Transactional
    public void enableModerator(Long userId) {
        Long tenantId = resolvedTenantId();
        CustomUserDetails actor = currentUserDetails();

        User moderator = requireModeratorInTenant(userId, tenantId);

        if (moderator.isEnabled()) {
            throw new ModeratorAlreadyEnabledException(
                    "Moderator account is already enabled");
        }

        // Restore account — status=ACTIVE, enabled=true
        // Old refresh tokens remain revoked. Moderator must log in normally.
        moderator.activate();
        userRepository.save(moderator);

        log.info("MODERATOR_ENABLED tenantId={} actorUserId={} targetUserId={}",
                tenantId, actor.getUserId(), userId);
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /**
     * Resolves the tenant ID from the HTTP request context.
     * Requires HttpTenantResolver to have a valid request in RequestContextHolder.
     */
    private Long resolvedTenantId() {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("Could not resolve tenant context");
        }
        return tenantId;
    }

    private CustomUserDetails currentUserDetails() {
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (details == null) {
            throw new AccessDeniedException("Authentication context is required");
        }
        return details;
    }

    /**
     * Loads a user that:
     * <ul>
     *   <li>exists and is not soft-deleted</li>
     *   <li>belongs to the given tenant</li>
     *   <li>has an active {@code RoleType.MODERATOR} account role</li>
     * </ul>
     *
     * <p>Cross-tenant IDs return NOT_FOUND semantics (same as non-existent IDs)
     * to avoid leaking resource existence information.
     *
     * <p>Role check is done via {@link UserRoleRepository} — never via {@code user.getUserRoles()}
     * to avoid Hibernate L1 cache staleness.
     */
    private User requireModeratorInTenant(Long userId, Long tenantId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ModeratorNotFoundException(
                        "Moderator not found: " + userId));

        // Tenant isolation — return NOT_FOUND to avoid resource existence leakage
        if (!tenantId.equals(user.getTenant().getId())) {
            throw new ModeratorNotFoundException(
                    "Moderator not found: " + userId);
        }

        // Role check via repository (authoritative source)
        boolean isModerator = userRoleRepository.existsActiveRoleByUserIdAndRoleName(
                userId, RoleType.MODERATOR);

        if (!isModerator) {
            throw new ModeratorNotFoundException(
                    "User " + userId + " does not have MODERATOR account role");
        }

        return user;
    }

}

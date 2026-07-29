package com.forumx.auth.staff.service;

import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.staff.dto.response.InvitationListResponse;
import com.forumx.auth.staff.dto.response.ModeratorDetailResponse;
import com.forumx.auth.staff.dto.response.ModeratorSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain service for Tenant Admin staff management operations.
 *
 * <p>All operations enforce:
 * <ul>
 *   <li>Tenant isolation: target must belong to the authenticated tenant.</li>
 *   <li>Role protection: endpoints only operate on MODERATOR account roles.</li>
 *   <li>Authorization: caller must be TENANT_ADMIN of the same tenant.</li>
 * </ul>
 *
 * <p>Repository queries are the authoritative source for role membership.
 * {@code user.getUserRoles()} is never used as the authoritative source.
 */
public interface TenantStaffService {

    /**
     * Lists moderator accounts belonging to the authenticated tenant.
     * Only users with an active {@code RoleType.MODERATOR} account role are returned.
     *
     * @param pageable pagination and sort parameters
     * @return page of moderator summaries
     */
    Page<ModeratorSummaryResponse> listModerators(Pageable pageable);

    /**
     * Returns the full profile of a single moderator.
     *
     * @param userId the target moderator's user ID
     * @return moderator detail response
     * @throws com.forumx.common.exception.ModeratorNotFoundException if user does not exist in tenant or lacks MODERATOR role
     */
    ModeratorDetailResponse getModeratorDetail(Long userId);

    /**
     * Lists moderator invitations belonging to the authenticated tenant.
     *
     * @param statusFilter optional status filter; null returns all statuses
     * @param pageable     pagination and sort parameters
     * @return page of invitation list responses (no tokenHash exposed)
     */
    Page<InvitationListResponse> listInvitations(InvitationStatus statusFilter, Pageable pageable);

    /**
     * Resends a moderator invitation by rotating credentials (new token, new expiry).
     * Allowed for PENDING and EXPIRED invitations only.
     *
     * @param invitationId the invitation to resend
     * @return updated invitation response (no tokenHash exposed)
     * @throws com.forumx.common.exception.InvitationNotFoundException        if not found in tenant
     * @throws com.forumx.common.exception.InvitationAlreadyAcceptedException if already accepted
     * @throws com.forumx.common.exception.InvitationAlreadyRevokedException  if already revoked
     */
    InvitationListResponse resendInvitation(Long invitationId);

    /**
     * Revokes a pending moderator invitation.
     * Allowed for PENDING invitations only. Expired invitations are also revocable
     * per documented domain rule.
     *
     * @param invitationId the invitation to revoke
     * @throws com.forumx.common.exception.InvitationNotFoundException        if not found in tenant
     * @throws com.forumx.common.exception.InvitationAlreadyAcceptedException if already accepted
     * @throws com.forumx.common.exception.InvitationAlreadyRevokedException  if already revoked
     */
    void revokeInvitation(Long invitationId);

    /**
     * Disables a moderator account.
     * <ol>
     *   <li>Marks user as INACTIVE / disabled via {@code user.deactivate()}</li>
     *   <li>Revokes all refresh tokens</li>
     *   <li>Terminates all active support-room participations</li>
     * </ol>
     * The next authenticated request using the moderator's existing access JWT will fail
     * because {@code JwtAuthenticationFilter} re-loads UserDetails from DB per request.
     *
     * @param userId the moderator to disable
     * @throws com.forumx.common.exception.ModeratorNotFoundException       if not found in tenant or lacks MODERATOR role
     * @throws com.forumx.common.exception.ModeratorAlreadyDisabledException if already disabled
     */
    void disableModerator(Long userId);

    /**
     * Re-enables a disabled moderator account.
     * Uses {@code user.activate()} to restore status=ACTIVE, enabled=true.
     * Old refresh tokens remain revoked; moderator must log in normally.
     *
     * @param userId the moderator to enable
     * @throws com.forumx.common.exception.ModeratorNotFoundException      if not found in tenant or lacks MODERATOR role
     * @throws com.forumx.common.exception.ModeratorAlreadyEnabledException if already enabled
     */
    void enableModerator(Long userId);
}

package com.forumx.auth.staff.controller;

import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.staff.dto.response.InvitationListResponse;
import com.forumx.auth.staff.dto.response.ModeratorDetailResponse;
import com.forumx.auth.staff.dto.response.ModeratorSummaryResponse;
import com.forumx.auth.staff.service.TenantStaffService;
import com.forumx.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Tenant Admin staff management (Phase C).
 *
 * <p>All endpoints require {@code TENANT_ADMIN} role. Tenant context is resolved
 * from the authenticated JWT — never from request parameters.
 *
 * <p>API surface:
 * <ul>
 *   <li>{@code GET  /api/v1/admin/staff/moderators} — list moderators</li>
 *   <li>{@code GET  /api/v1/admin/staff/moderators/{userId}} — moderator detail</li>
 *   <li>{@code POST /api/v1/admin/staff/moderators/{userId}/disable} — disable moderator</li>
 *   <li>{@code POST /api/v1/admin/staff/moderators/{userId}/enable} — enable moderator</li>
 *   <li>{@code GET  /api/v1/admin/staff/invitations} — list invitations</li>
 *   <li>{@code POST /api/v1/admin/staff/invitations/{id}/resend} — resend invitation</li>
 *   <li>{@code POST /api/v1/admin/staff/invitations/{id}/revoke} — revoke invitation</li>
 * </ul>
 *
 * <p>The Phase B invitation creation endpoint ({@code POST /api/v1/admin/staff/invitations})
 * is managed by {@link com.forumx.auth.invitation.controller.AdminStaffInvitationController}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/staff")
@Tag(name = "Tenant Staff Management", description = "Tenant Admin operations for managing moderators and invitations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantStaffController {

    private final TenantStaffService tenantStaffService;

    // ── Moderators ──────────────────────────────────────────────────────

    @GetMapping("/moderators")
    @Operation(summary = "List Moderators",
               description = "Returns paginated list of MODERATOR accounts belonging to the authenticated tenant")
    public ResponseEntity<ApiResponse<Page<ModeratorSummaryResponse>>> listModerators(
            @PageableDefault(size = 20, sort = "username") Pageable pageable
    ) {
        Page<ModeratorSummaryResponse> page = tenantStaffService.listModerators(pageable);
        return ResponseEntity.ok(ApiResponse.success("Moderators retrieved successfully", page));
    }

    @GetMapping("/moderators/{userId}")
    @Operation(summary = "Get Moderator Detail",
               description = "Returns full profile of a single MODERATOR in the authenticated tenant")
    public ResponseEntity<ApiResponse<ModeratorDetailResponse>> getModeratorDetail(
            @PathVariable Long userId
    ) {
        ModeratorDetailResponse detail = tenantStaffService.getModeratorDetail(userId);
        return ResponseEntity.ok(ApiResponse.success("Moderator retrieved successfully", detail));
    }

    @PostMapping("/moderators/{userId}/disable")
    @Operation(summary = "Disable Moderator",
               description = "Disables a MODERATOR account, revokes refresh tokens, and terminates active support participations")
    public ResponseEntity<ApiResponse<Void>> disableModerator(
            @PathVariable Long userId
    ) {
        tenantStaffService.disableModerator(userId);
        return ResponseEntity.ok(ApiResponse.success("Moderator disabled successfully", null));
    }

    @PostMapping("/moderators/{userId}/enable")
    @Operation(summary = "Enable Moderator",
               description = "Re-enables a disabled MODERATOR account. Moderator must log in normally to obtain new tokens")
    public ResponseEntity<ApiResponse<Void>> enableModerator(
            @PathVariable Long userId
    ) {
        tenantStaffService.enableModerator(userId);
        return ResponseEntity.ok(ApiResponse.success("Moderator enabled successfully", null));
    }

    // ── Invitations ─────────────────────────────────────────────────────

    @GetMapping("/invitations")
    @Operation(summary = "List Invitations",
               description = "Returns paginated list of moderator invitations for the authenticated tenant. Optionally filter by status")
    public ResponseEntity<ApiResponse<Page<InvitationListResponse>>> listInvitations(
            @Parameter(description = "Optional status filter: PENDING, ACCEPTED, EXPIRED, REVOKED")
            @RequestParam(required = false) InvitationStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<InvitationListResponse> page = tenantStaffService.listInvitations(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Invitations retrieved successfully", page));
    }

    @PostMapping("/invitations/{invitationId}/resend")
    @Operation(summary = "Resend Invitation",
               description = "Rotates the invitation token (new hash, new expiry) and dispatches a fresh invitation email. Allowed for PENDING and EXPIRED invitations only")
    public ResponseEntity<ApiResponse<InvitationListResponse>> resendInvitation(
            @PathVariable Long invitationId
    ) {
        InvitationListResponse response = tenantStaffService.resendInvitation(invitationId);
        return ResponseEntity.ok(ApiResponse.success("Invitation resent successfully", response));
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    @Operation(summary = "Revoke Invitation",
               description = "Revokes a pending moderator invitation. The old token immediately becomes invalid")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @PathVariable Long invitationId
    ) {
        tenantStaffService.revokeInvitation(invitationId);
        return ResponseEntity.ok(ApiResponse.success("Invitation revoked successfully", null));
    }
}

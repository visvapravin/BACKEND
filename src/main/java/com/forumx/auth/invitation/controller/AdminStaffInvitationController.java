package com.forumx.auth.invitation.controller;

import com.forumx.auth.invitation.dto.request.CreateInvitationRequest;
import com.forumx.auth.invitation.dto.response.InvitationResponse;
import com.forumx.auth.invitation.service.InvitationService;
import com.forumx.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/staff")
@Tag(name = "Tenant Staff Management", description = "Admin operations for inviting and managing tenant staff")
@SecurityRequirement(name = "bearerAuth")
public class AdminStaffInvitationController {

    private final InvitationService invitationService;

    @PostMapping("/invitations")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Invite Moderator", description = "Creates a new Moderator invitation for the specified email under the active tenant and dispatches an invitation email")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteModerator(
            @Valid @RequestBody CreateInvitationRequest request
    ) {
        InvitationResponse response = invitationService.createModeratorInvitation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Moderator invitation created successfully", response));
    }
}

package com.forumx.platform.invitation.controller;

import com.forumx.common.dto.ApiResponse;
import com.forumx.platform.invitation.dto.CreateTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.TenantAdminInvitationResponse;
import com.forumx.platform.invitation.service.TenantAdminInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform/tenants/{tenantId}/admin-invitations")
@Tag(name = "Platform Tenant Admin Invitations", description = "Platform Admin operations for inviting initial TENANT_ADMIN")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantAdminInvitationController {

    private final TenantAdminInvitationService invitationService;

    @PostMapping
    @Operation(summary = "Invite Tenant Admin", description = "Dispatches a TENANT_ADMIN invitation for the specified tenant. Restricted to PLATFORM_ADMIN.")
    public ResponseEntity<ApiResponse<TenantAdminInvitationResponse>> createInvitation(
            @PathVariable Long tenantId,
            @Valid @RequestBody CreateTenantAdminInvitationRequest request
    ) {
        TenantAdminInvitationResponse response = invitationService.createInvitation(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant Admin invitation created successfully", response));
    }
}

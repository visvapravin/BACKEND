package com.forumx.platform.invitation.controller;

import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationResponse;
import com.forumx.common.dto.ApiResponse;
import com.forumx.platform.invitation.dto.AcceptTenantAdminInvitationRequest;
import com.forumx.platform.invitation.dto.ValidateTenantAdminInvitationResponse;
import com.forumx.platform.invitation.service.TenantAdminInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/tenant-admin-invitations")
@Tag(name = "Tenant Admin Invitation Acceptance", description = "Public operations for validating and accepting TENANT_ADMIN invitations")
public class PublicTenantAdminInvitationController {

    private final TenantAdminInvitationService invitationService;

    @GetMapping("/validate")
    @Operation(summary = "Validate Tenant Admin Invitation", description = "Checks validity of raw invitation token and returns safe metadata")
    public ResponseEntity<ApiResponse<ValidateTenantAdminInvitationResponse>> validateInvitation(
            @RequestParam("token") String token
    ) {
        ValidateTenantAdminInvitationResponse response = invitationService.validateInvitation(token);
        return ResponseEntity.ok(ApiResponse.success("Invitation validated successfully", response));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept Tenant Admin Invitation", description = "Accepts invitation, creates TENANT_ADMIN user bound to tenant. The user must subsequently log in via POST /api/v1/auth/login with the X-Tenant header.")
    public ResponseEntity<ApiResponse<AcceptTenantAdminInvitationResponse>> acceptInvitation(
            @Valid @RequestBody AcceptTenantAdminInvitationRequest request,
            HttpServletRequest servletRequest
    ) {
        AcceptTenantAdminInvitationResponse response = invitationService.acceptInvitation(request, servletRequest);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }
}

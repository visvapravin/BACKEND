package com.forumx.auth.invitation.controller;

import com.forumx.auth.invitation.dto.request.AcceptInvitationRequest;
import com.forumx.auth.invitation.dto.response.AcceptInvitationResponse;
import com.forumx.auth.invitation.dto.response.InvitationValidationResponse;
import com.forumx.auth.invitation.service.InvitationService;
import com.forumx.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/invitations")
@Tag(name = "Public Moderator Invitations", description = "Public APIs for validating and accepting staff invitations")
public class PublicInvitationController {

    private final InvitationService invitationService;

    @GetMapping("/validate")
    @Operation(summary = "Validate Invitation Token", description = "Validates an invitation token and returns non-sensitive metadata for token verification")
    public ResponseEntity<ApiResponse<InvitationValidationResponse>> validateInvitation(
            @RequestParam("token") String token
    ) {
        InvitationValidationResponse response = invitationService.validateInvitation(token);
        return ResponseEntity.ok(ApiResponse.success("Invitation token is valid", response));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept Moderator Invitation", description = "Accepts an invitation, creates the moderator account, and marks the invitation ACCEPTED")
    public ResponseEntity<ApiResponse<AcceptInvitationResponse>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request
    ) {
        AcceptInvitationResponse response = invitationService.acceptInvitation(request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }
}

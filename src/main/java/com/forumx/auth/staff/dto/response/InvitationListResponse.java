package com.forumx.auth.staff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Safe response DTO for the invitation list endpoint.
 * NEVER exposes tokenHash or raw token values.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvitationListResponse {

    private Long id;
    private String email;
    private RoleType role;

    /**
     * The effective display status — PENDING invitations past their expiresAt
     * are reported as EXPIRED for consistency with validation/acceptance endpoints.
     */
    private InvitationStatus status;

    private Instant expiresAt;
    private Instant acceptedAt;
    private Instant revokedAt;
    private Instant createdAt;

    /** Safe inviter summary — username only, no user ID leakage to frontend. */
    private String invitedByUsername;

    /**
     * Constructs a safe invitation list response from a ModeratorInvitation entity.
     * Expired-but-still-PENDING invitations are reported as EXPIRED (C5 consistency).
     */
    public static InvitationListResponse from(ModeratorInvitation invitation) {
        InvitationStatus effectiveStatus = invitation.getStatus();
        if (effectiveStatus == InvitationStatus.PENDING && invitation.isExpired()) {
            effectiveStatus = InvitationStatus.EXPIRED;
        }

        return InvitationListResponse.builder()
                .id(invitation.getId())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .status(effectiveStatus)
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .revokedAt(invitation.getRevokedAt())
                .createdAt(invitation.getCreatedAt())
                .invitedByUsername(invitation.getInvitedBy() != null
                        ? invitation.getInvitedBy().getUsername()
                        : null)
                .build();
    }
}

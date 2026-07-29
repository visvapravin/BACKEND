package com.forumx.auth.invitation.dto.response;

import java.time.Instant;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.entity.InvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    private Long id;
    private Long tenantId;
    private String email;
    private RoleType role;
    private InvitationStatus status;
    private String invitedByUsername;
    private Instant expiresAt;
    private Instant createdAt;
}

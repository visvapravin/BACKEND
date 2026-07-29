package com.forumx.platform.invitation.dto;

import java.time.Instant;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.invitation.entity.InvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdminInvitationResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String email;
    private RoleType role;
    private InvitationStatus status;
    private Instant expiresAt;
    private Instant createdAt;
}

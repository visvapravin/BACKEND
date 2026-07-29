package com.forumx.platform.invitation.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTenantAdminInvitationResponse {
    private String tenantName;
    private String tenantSlug;
    private String email;
    private Instant expiresAt;
    private boolean valid;
}

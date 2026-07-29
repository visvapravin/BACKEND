package com.forumx.auth.invitation.dto.response;

import java.time.Instant;
import com.forumx.auth.enums.RoleType;
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
public class InvitationValidationResponse {
    private boolean valid;
    private String email;
    private String tenantName;
    private RoleType role;
    private Instant expiresAt;
}

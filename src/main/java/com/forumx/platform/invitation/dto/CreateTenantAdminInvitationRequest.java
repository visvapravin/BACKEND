package com.forumx.platform.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantAdminInvitationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email address is required")
    private String email;
}

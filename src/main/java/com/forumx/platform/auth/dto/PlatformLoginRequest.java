package com.forumx.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PlatformLoginRequest {
    @NotBlank private String usernameOrEmail;
    @NotBlank private String password;
}

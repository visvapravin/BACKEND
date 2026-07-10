package com.forumx.auth.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object representing the response payload for a successful login.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Instant expiresAt;
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    private Long tenantId;
    private List<String> roles;
    private List<String> permissions;
}

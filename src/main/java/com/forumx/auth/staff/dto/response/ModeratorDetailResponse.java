package com.forumx.auth.staff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forumx.auth.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Detailed view of a moderator account returned by the Get Moderator Detail endpoint.
 * Never exposes password hashes, refresh tokens, or security internals.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModeratorDetailResponse {

    private Long userId;
    private String username;
    private String email;
    private boolean enabled;
    private String status;
    private boolean emailVerified;
    private boolean accountLocked;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    /**
     * Constructs a safe detail response from a fully-loaded User entity.
     */
    public static ModeratorDetailResponse from(User user) {
        return ModeratorDetailResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .status(user.getStatus().name())
                .emailVerified(user.isEmailVerified())
                .accountLocked(user.isAccountLocked())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}

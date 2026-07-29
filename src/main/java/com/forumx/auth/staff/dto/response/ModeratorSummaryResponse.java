package com.forumx.auth.staff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forumx.auth.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Summary view of a moderator account returned by the List Moderators endpoint.
 * Never exposes password hashes, refresh tokens, or security internals.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModeratorSummaryResponse {

    private Long userId;
    private String username;
    private String email;
    private boolean enabled;
    private String status;
    private boolean emailVerified;
    private Instant createdAt;
    private Instant lastLoginAt;

    /**
     * Constructs a safe summary from a fully-loaded User entity.
     */
    public static ModeratorSummaryResponse from(User user) {
        return ModeratorSummaryResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .status(user.getStatus().name())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}

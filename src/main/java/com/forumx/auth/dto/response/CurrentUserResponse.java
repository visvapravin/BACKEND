package com.forumx.auth.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object representing the currently authenticated user's profile.
 * Exposes only frontend-safe fields for client consumption.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    // ── Identity ────────────────────────────────────────────────────────

    private Long userId;
    private String username;
    private String email;
    private String phoneNumber;

    // ── Profile ─────────────────────────────────────────────────────────

    private String displayName;
    private String firstName;
    private String lastName;

    // ── Tenant ──────────────────────────────────────────────────────────

    private Long tenantId;
    private String tenantSlug;

    // ── Account status ──────────────────────────────────────────────────

    private boolean active;
    private boolean enabled;
    private boolean accountNonLocked;
    private boolean accountNonExpired;
    private boolean credentialsNonExpired;

    // ── Authorization ───────────────────────────────────────────────────

    private List<String> roles;
    private List<String> permissions;
}

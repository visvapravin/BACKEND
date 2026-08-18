package com.forumx.platform.invitation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response returned when a Tenant Admin invitation is successfully accepted.
 *
 * <p>This is an honest response: no access/refresh tokens are issued here.
 * The invitee must authenticate via the standard tenant login endpoint
 * ({@code POST /api/v1/auth/login} with the {@code X-Tenant} header).
 *
 * <p>The {@code loginRequired=true} field signals the frontend to redirect
 * to the login page instead of attempting auto-login.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptTenantAdminInvitationResponse {

    /** Human-readable confirmation message. */
    private String message;

    /**
     * Always {@code true} — the invitee must login via
     * {@code POST /api/v1/auth/login} with the {@code X-Tenant} header
     * to obtain an access token.
     */
    @Builder.Default
    private boolean loginRequired = true;

    /** The username that was created for this account. */
    private String username;

    /** The email address this invitation was sent to. */
    private String email;

    /** The tenant slug the user was registered under. Used to construct the login request. */
    private String tenantSlug;

    /**
     * Frontend navigation hint. Value: {@code "PROCEED_TO_LOGIN"}.
     * The frontend should redirect to the login page using {@code tenantSlug}.
     */
    @Builder.Default
    private String nextAction = "PROCEED_TO_LOGIN";
}

package com.forumx.security.jwt;

/**
 * Constants for JSON Web Token (JWT) claim keys.
 */
public final class JwtClaimsConstants {

    public static final String USERNAME = "username";
    public static final String EMAIL = "email";
    public static final String USER_ID = "user_id";
    public static final String TENANT_ID = "tenant_id";
    public static final String TENANT_SLUG = "tenant_slug";
    public static final String SCOPE = "scope";
    public static final String DISPLAY_NAME = "display_name";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String TOKEN_TYPE = "token_type";
    public static final String SESSION_ID = "session_id";

    private JwtClaimsConstants() {
        // Prevent instantiation
    }
}

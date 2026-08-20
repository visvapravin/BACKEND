package com.forumx.redis.constant;

/**
 * Centralised Redis key namespace constants and key-builder helpers for ForumX.
 *
 * <h3>Convention</h3>
 * All keys follow the pattern: {@code forumx:{namespace}:{identifier}}
 *
 * <h3>Scope</h3>
 * This class contains only <em>infrastructure-level</em> key builders. Domain-specific
 * keys (e.g. {@code notification:unread:{userId}}, {@code presence:online:{tenantId}})
 * belong in the modules that own those concepts — not here.
 *
 * <h3>Reserved namespaces</h3>
 * {@link #PRESENCE_NS} and {@link #RATE_NS} are reserved for future modules. Keys under
 * these namespaces should only be introduced when those modules are built.
 */
public final class RedisKeys {

    private RedisKeys() {}

    private static final String SEP = ":";

    // -------------------------------------------------------------------------
    // Namespace constants
    // -------------------------------------------------------------------------

    public static final String SESSION_NS  = "forumx:session";
    public static final String USER_NS     = "forumx:user";
    public static final String TENANT_NS   = "forumx:tenant";
    public static final String WEBSOCKET_NS = "forumx:ws";
    public static final String CACHE_NS    = "forumx:cache";

    /** Reserved — future Online Presence module. */
    public static final String PRESENCE_NS = "forumx:presence";

    /** Reserved — future Rate Limiting module. */
    public static final String RATE_NS     = "forumx:rate";

    public static String presence(long userId) {
        return PRESENCE_NS + ":" + "user" + ":" + userId;
    }

    public static String presenceSessions(long userId) {
        return PRESENCE_NS + ":" + "sessions" + ":" + userId;
    }

    public static String presenceSession(String sessionId) {
        return PRESENCE_NS + ":" + "session" + ":" + sessionId;
    }

    public static String presenceTenantOnline(long tenantId) {
        return PRESENCE_NS + ":" + "tenant" + ":" + tenantId + ":" + "online";
    }

    /**
     * Tenant-scoped session membership set for a specific user.
     * Holds all active sessionIds for the given user within the given tenant.
     * Example: {@code forumx:presence:tenant:3:user:42:sessions}
     *
     * <p>Use {@link #presenceSessions(long)} for the legacy global sessions key.
     */
    public static String presenceTenantUserSessions(long tenantId, long userId) {
        return PRESENCE_NS + ":" + "tenant" + ":" + tenantId + ":" + "user" + ":" + userId + ":" + "sessions";
    }

    // -------------------------------------------------------------------------
    // Generic key builders (infrastructure level only)
    // -------------------------------------------------------------------------

    /**
     * Key for a user's distributed session data.
     * Example: {@code forumx:session:1042}
     */
    public static String session(long userId) {
        return SESSION_NS + SEP + userId;
    }

    /**
     * Key for generic user-scoped data.
     * Example: {@code forumx:user:1042}
     */
    public static String user(long userId) {
        return USER_NS + SEP + userId;
    }

    /**
     * Key for tenant-scoped data.
     * Example: {@code forumx:tenant:7}
     */
    public static String tenant(long tenantId) {
        return TENANT_NS + SEP + tenantId;
    }

    /**
     * Key for a WebSocket session context stored in Redis.
     * Example: {@code forumx:ws:session:abc-123}
     */
    public static String websocketSession(String sessionId) {
        return WEBSOCKET_NS + SEP + "session" + SEP + sessionId;
    }

    /**
     * Key for a named cache entry (useful for manual cache operations via
     * {@link com.forumx.redis.gateway.RedisGateway} when {@code @Cacheable} is insufficient).
     * Example: {@code forumx:cache:question-detail}
     */
    public static String cache(String name) {
        return CACHE_NS + SEP + name;
    }
}

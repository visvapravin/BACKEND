package com.forumx.redis.constant;

/**
 * Redis Pub/Sub channel name constants for ForumX.
 *
 * <h3>Naming convention</h3>
 * All channel names follow the pattern: {@code forumx.events.{domain}}
 * The {@code _CHANNEL} suffix makes usage at call sites self-documenting:
 * <pre>
 *   redisPublisher.publish(RedisTopicConstants.USER_EVENT_CHANNEL, event);
 * </pre>
 *
 * <h3>Scope</h3>
 * Only infrastructure-level channels are defined here. Domain-specific sub-channels
 * (e.g. {@code forumx.events.notification.user.{id}}) should be defined in the modules
 * that own those concepts.
 */
public final class RedisTopicConstants {

    private RedisTopicConstants() {}

    /** Channel for tenant-scoped events (e.g. tenant configuration changes). */
    public static final String TENANT_EVENT_CHANNEL = "forumx.events.tenant";

    /** Channel for user-scoped events (e.g. profile updates, session invalidation). */
    public static final String USER_EVENT_CHANNEL = "forumx.events.user";

    /** Channel for system-wide broadcast messages delivered to all nodes. */
    public static final String SYSTEM_BROADCAST_CHANNEL = "forumx.events.broadcast";

    /** Channel for WebSocket infrastructure events (e.g. cross-node session notifications). */
    public static final String WEBSOCKET_EVENT_CHANNEL = "forumx.events.websocket";
}

package com.forumx.redis.pubsub;

import org.springframework.data.redis.connection.MessageListener;

/**
 * Stateless Redis Pub/Sub subscriber registered automatically by {@code RedisConfig}.
 * Implementations must not retain mutable per-message or per-user state because callbacks
 * may be invoked concurrently by Redis listener threads.
 */
public interface RedisSubscriber extends MessageListener {
    String getChannel();
}

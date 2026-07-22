package com.forumx.redis.pubsub;

import java.time.Instant;
import java.util.UUID;

/** Standard, traceable envelope for all ForumX Redis Pub/Sub messages. */
public record RedisEvent<T>(UUID eventId, String eventType, Instant timestamp, T payload) {
    public static <T> RedisEvent<T> of(String eventType, T payload) {
        return new RedisEvent<>(UUID.randomUUID(), eventType, Instant.now(), payload);
    }
}

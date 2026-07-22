package com.forumx.redis.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.redis.exception.RedisOperationException;
import com.forumx.redis.serializer.RedisObjectSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Publishes standard {@link RedisEvent} envelopes to Redis channels. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.redis.enabled", havingValue = "true")
public class RedisPublisher {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = RedisObjectSerializer.createObjectMapper();

    public void publish(String channel, RedisEvent<?> event) {
        try {
            stringRedisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new RedisOperationException("Failed to publish Redis event to channel: " + channel, exception);
        }
    }
}

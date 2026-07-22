package com.forumx.redis.gateway;

import java.time.Duration;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.redis.exception.RedisOperationException;
import com.forumx.redis.serializer.RedisObjectSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Sole implementation allowed to perform explicit Redis value operations. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.redis.enabled", havingValue = "true")
public class RedisGatewayImpl implements RedisGateway {
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = RedisObjectSerializer.createObjectMapper();

    @Override public <T> void put(String key, T value) { execute("store key", () -> { redisTemplate.opsForValue().set(key, value); return null; }); }
    @Override public <T> void put(String key, T value, Duration ttl) { execute("store key with TTL", () -> { redisTemplate.opsForValue().set(key, value, ttl); return null; }); }
    @Override public <T> Optional<T> get(String key, Class<T> type) { return execute("read key", () -> Optional.ofNullable(redisTemplate.opsForValue().get(key)).map(value -> objectMapper.convertValue(value, type))); }
    @Override public boolean delete(String key) { return execute("delete key", () -> Boolean.TRUE.equals(redisTemplate.delete(key))); }
    @Override public boolean exists(String key) { return execute("check key", () -> Boolean.TRUE.equals(redisTemplate.hasKey(key))); }
    @Override public boolean expire(String key, Duration ttl) { return execute("expire key", () -> Boolean.TRUE.equals(redisTemplate.expire(key, ttl))); }
    @Override public long increment(String key) { return increment(key, 1); }
    @Override public long increment(String key, long delta) { return execute("increment key", () -> { Long value = stringRedisTemplate.opsForValue().increment(key, delta); return value == null ? 0L : value; }); }
    @Override public long sAdd(String key, String... values) { return execute("sAdd", () -> { Long added = stringRedisTemplate.opsForSet().add(key, values); return added == null ? 0L : added; }); }
    @Override public long sRem(String key, String... values) { return execute("sRem", () -> { Long removed = stringRedisTemplate.opsForSet().remove(key, (Object[]) values); return removed == null ? 0L : removed; }); }
    @Override public long sCard(String key) { return execute("sCard", () -> { Long size = stringRedisTemplate.opsForSet().size(key); return size == null ? 0L : size; }); }
    @Override public java.util.Set<String> sMembers(String key) { return execute("sMembers", () -> stringRedisTemplate.opsForSet().members(key)); }

    private <T> T execute(String operation, RedisOperation<T> action) {
        try { return action.execute(); }
        catch (RedisOperationException exception) { throw exception; }
        catch (RuntimeException exception) { throw new RedisOperationException("Failed to " + operation, exception); }
    }
    @FunctionalInterface private interface RedisOperation<T> { T execute(); }
}

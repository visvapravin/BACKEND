package com.forumx.redis.serializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

/**
 * Factory for the Redis-specific Jackson serializer configuration.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Uses {@link Jackson2JsonRedisSerializer} (NOT {@code GenericJackson2JsonRedisSerializer})
 *       to avoid embedding {@code @class} type metadata in every stored JSON value.</li>
 *   <li>Registers {@link JavaTimeModule} so {@link java.time.Instant}, {@link java.time.LocalDate},
 *       etc. are serialized as ISO-8601 strings, not numeric timestamps.</li>
 *   <li>Default typing is intentionally disabled. Callers must specify the target type
 *       explicitly at read time (via {@code RedisGateway.get(key, Class<T>)}).</li>
 * </ul>
 *
 * <p>This is intentionally a static utility class — it produces a configured
 * {@link ObjectMapper} independent of the Spring application context's primary
 * {@code ObjectMapper} bean, giving Redis its own isolated serialization settings.
 */
public final class RedisObjectSerializer {

    private RedisObjectSerializer() {}

    /**
     * Creates an {@link ObjectMapper} configured for Redis value serialization.
     *
     * @return a fresh, fully configured {@code ObjectMapper}
     */
    public static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Creates a {@link Jackson2JsonRedisSerializer} backed by the Redis-specific
     * {@link ObjectMapper}.
     *
     * @return a configured serializer for {@link Object} values
     */
    public static Jackson2JsonRedisSerializer<Object> createSerializer() {
        return new Jackson2JsonRedisSerializer<>(createObjectMapper(), Object.class);
    }
}

package com.forumx.redis.gateway;

import java.time.Duration;
import java.util.Optional;

/**
 * Gateway interface for explicit Redis operations in ForumX.
 *
 * <h3>Responsibility boundary</h3>
 * {@code RedisGateway} covers CRUD, TTL management, and atomic counters — operations that
 * require direct Redis interaction and are not well served by Spring Cache annotations.
 *
 * <p><strong>For standard caching use cases</strong> ({@code @Cacheable}, {@code @CacheEvict},
 * {@code @CachePut}), use Spring's cache abstraction backed by the configured
 * {@link org.springframework.data.redis.cache.RedisCacheManager} instead.
 *
 * <p><strong>For Pub/Sub</strong>, use {@link com.forumx.redis.pubsub.RedisPublisher}.
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li>Only {@link com.forumx.redis.gateway.impl.RedisGatewayImpl} may inject
 *       {@code RedisTemplate}. Business modules must depend on this interface.</li>
 *   <li>All failures are wrapped in
 *       {@link com.forumx.redis.exception.RedisOperationException}.</li>
 *   <li>{@code get()} returns {@link Optional#empty()} for missing keys — never {@code null}.</li>
 * </ul>
 */
public interface RedisGateway {

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Stores {@code value} at {@code key} with no expiration. */
    <T> void put(String key, T value);

    /** Stores {@code value} at {@code key}, expiring after {@code ttl}. */
    <T> void put(String key, T value, Duration ttl);

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Retrieves the value at {@code key} and deserializes it to {@code type}.
     *
     * @param key  Redis key
     * @param type target class for deserialization
     * @return {@link Optional#empty()} if the key does not exist
     */
    <T> Optional<T> get(String key, Class<T> type);

    // -------------------------------------------------------------------------
    // Delete / Existence
    // -------------------------------------------------------------------------

    /**
     * Deletes the key.
     *
     * @return {@code true} if the key existed and was deleted; {@code false} if it was absent
     */
    boolean delete(String key);

    /** Returns {@code true} if the key exists in Redis. */
    boolean exists(String key);

    // -------------------------------------------------------------------------
    // TTL
    // -------------------------------------------------------------------------

    /**
     * Sets or updates the TTL on an existing key.
     *
     * @return {@code true} if the key existed and the TTL was applied; {@code false} otherwise
     */
    boolean expire(String key, Duration ttl);

    // -------------------------------------------------------------------------
    // Counters
    // -------------------------------------------------------------------------

    /**
     * Atomically increments the counter at {@code key} by 1.
     * If the key does not exist, it is initialised to 0 before incrementing.
     *
     * <p><strong>Note:</strong> Counter keys use plain string encoding and must NOT overlap
     * with JSON-serialized keys written via {@link #put}.
     *
     * @return the value after increment
     */
    long increment(String key);

    /**
     * Atomically increments the counter at {@code key} by {@code delta}.
     *
     * @param delta amount to add (may be negative for decrement)
     * @return the value after increment
     */
    long increment(String key, long delta);

    // -------------------------------------------------------------------------
    // Sets
    // -------------------------------------------------------------------------

    /** Adds {@code values} to the set at {@code key}. */
    long sAdd(String key, String... values);

    /** Removes {@code values} from the set at {@code key}. */
    long sRem(String key, String... values);

    /** Returns the size (cardinality) of the set at {@code key}. */
    long sCard(String key);

    /** Returns all members of the set at {@code key}. */
    java.util.Set<String> sMembers(String key);
}

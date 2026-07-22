package com.forumx.redis.gateway;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback in-memory implementation of {@link RedisGateway} used when Redis is disabled.
 *
 * <p>This implementation ensures that the Spring Application Context can be loaded and
 * functionality dependent on {@code RedisGateway} can degrade gracefully without causing
 * startup errors.
 */
@Component
@ConditionalOnProperty(name = "forumx.redis.enabled", havingValue = "false")
public class InMemoryRedisGateway implements RedisGateway {

    private final ConcurrentMap<String, Object> store = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> setStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> counters = new ConcurrentHashMap<>();

    @Override
    public <T> void put(String key, T value) {
        if (value == null) {
            store.remove(key);
        } else {
            store.put(key, value);
        }
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object val = store.get(key);
        if (val == null) {
            return Optional.empty();
        }
        if (type.isInstance(val)) {
            return Optional.of((T) val);
        }
        return Optional.empty();
    }

    @Override
    public boolean delete(String key) {
        boolean existed = store.remove(key) != null;
        boolean setExisted = setStore.remove(key) != null;
        boolean counterExisted = counters.remove(key) != null;
        return existed || setExisted || counterExisted;
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key) || setStore.containsKey(key) || counters.containsKey(key);
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        return exists(key);
    }

    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long delta) {
        return counters.compute(key, (k, v) -> (v == null ? 0L : v) + delta);
    }

    @Override
    public long sAdd(String key, String... values) {
        Set<String> set = setStore.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        long added = 0;
        for (String val : values) {
            if (set.add(val)) {
                added++;
            }
        }
        return added;
    }

    @Override
    public long sRem(String key, String... values) {
        Set<String> set = setStore.get(key);
        if (set == null) {
            return 0;
        }
        long removed = 0;
        for (String val : values) {
            if (set.remove(val)) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public long sCard(String key) {
        Set<String> set = setStore.get(key);
        return set == null ? 0 : set.size();
    }

    @Override
    public Set<String> sMembers(String key) {
        Set<String> set = setStore.get(key);
        return set == null ? Set.of() : Set.copyOf(set);
    }
}

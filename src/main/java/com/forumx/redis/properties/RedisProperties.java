package com.forumx.redis.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe configuration properties for the ForumX Redis infrastructure.
 *
 * <p>Bound from the {@code forumx.redis.*} namespace in {@code application.yml}.
 * All values can be overridden via environment variables (see {@code application.yml} for
 * the {@code ${ENV_VAR:default}} mappings).
 *
 * <p>Redis is disabled by default in the {@code dev} profile so that local development
 * and standard integration tests can run without a Redis server. Tests that specifically
 * exercise Redis functionality use Testcontainers and enable Redis via
 * {@code @DynamicPropertySource}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "forumx.redis")
public class RedisProperties {

    /** Whether the Redis infrastructure is active. Defaults to {@code true} in production,
     *  {@code false} in the dev profile. */
    private boolean enabled = false;

    private String host = "localhost";
    private int port = 6379;

    /** Leave empty for Redis instances without authentication. */
    private String password = "";

    private int database = 0;
    private boolean ssl = false;

    /** Command timeout. Governs how long Lettuce waits for a Redis command response. */
    private Duration timeout = Duration.ofSeconds(5);

    private Cache cache = new Cache();
    private Pool pool = new Pool();

    @Getter
    @Setter
    public static class Cache {
        /** Default TTL applied to all cache entries managed by {@code RedisCacheManager}. */
        private Duration ttl = Duration.ofMinutes(60);

        /** Key prefix prepended to all cache names to namespace them in Redis. */
        private String keyPrefix = "forumx:cache:";
    }

    @Getter
    @Setter
    public static class Pool {
        /** Maximum number of connections in the pool. -1 means unlimited. */
        private int maxActive = 8;

        /** Maximum number of idle connections in the pool. */
        private int maxIdle = 8;

        /** Minimum number of idle connections to maintain. */
        private int minIdle = 0;

        /** Maximum time to wait for a connection. -1 means block indefinitely. */
        private Duration maxWait = Duration.ofMillis(-1);
    }
}

package com.forumx.redis.config;

import com.forumx.redis.properties.RedisProperties;
import com.forumx.redis.pubsub.RedisSubscriber;
import com.forumx.redis.serializer.RedisObjectSerializer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

/**
 * Central Redis infrastructure configuration for ForumX.
 *
 * <h3>Activation</h3>
 * Active only when {@code forumx.redis.enabled=true}. In the {@code dev} profile this
 * property is {@code false}, so no Redis connection is attempted during local development
 * or standard integration tests. Redis-specific tests enable it via Testcontainers and
 * {@code @DynamicPropertySource}.
 *
 * <h3>Beans provided</h3>
 * <ul>
 *   <li>{@link LettuceConnectionFactory} — self-contained, built from {@link RedisProperties}.
 *       Does NOT rely on Spring Boot's {@code RedisAutoConfiguration}.</li>
 *   <li>{@link RedisTemplate}{@code <String, Object>} — String keys, Jackson2Json values
 *       (no {@code @class} metadata, ISO-8601 dates).</li>
 *   <li>{@link StringRedisTemplate} — for raw string/counter operations (e.g. INCR).</li>
 *   <li>{@link RedisCacheManager} — powers {@code @Cacheable}, {@code @CacheEvict},
 *       {@code @CachePut} across all business modules.</li>
 *   <li>{@link RedisMessageListenerContainer} — auto-registers every {@link RedisSubscriber}
 *       bean found in the application context at startup.</li>
 * </ul>
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.redis.enabled", havingValue = "true")
public class RedisConfig {

    private final RedisProperties redisProperties;

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(redisProperties.getHost());
        serverConfig.setPort(redisProperties.getPort());
        serverConfig.setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            serverConfig.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }

        // Connection pool configuration — driven by forumx.redis.pool.*
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(redisProperties.getPool().getMaxActive());
        poolConfig.setMaxIdle(redisProperties.getPool().getMaxIdle());
        poolConfig.setMinIdle(redisProperties.getPool().getMinIdle());
        poolConfig.setMaxWait(redisProperties.getPool().getMaxWait());
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder clientBuilder =
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(redisProperties.getTimeout())
                        .poolConfig(poolConfig);
        if (redisProperties.isSsl()) {
            clientBuilder.useSsl();
        }

        return new LettuceConnectionFactory(serverConfig, clientBuilder.build());
    }

    // -------------------------------------------------------------------------
    // Templates
    // -------------------------------------------------------------------------

    /**
     * General-purpose Redis template for storing and retrieving JSON-serialized objects.
     *
     * <p>Key serializer: {@link StringRedisSerializer} (human-readable keys in Redis CLI).
     * Value serializer: {@link com.forumx.redis.serializer.RedisObjectSerializer} (Jackson, no
     * default typing, ISO-8601 dates).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        template.setConnectionFactory(factory);
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(RedisObjectSerializer.createSerializer());
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(RedisObjectSerializer.createSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * String-only Redis template used for atomic counter operations ({@code INCR},
     * {@code INCRBY}) and plain string values.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // -------------------------------------------------------------------------
    // Cache
    // -------------------------------------------------------------------------

    /**
     * {@link RedisCacheManager} that powers Spring Cache annotations across business modules.
     *
     * <p><strong>Usage guidance for contributors:</strong> Business services should prefer
     * {@code @Cacheable} / {@code @CacheEvict} / {@code @CachePut} whenever the data access
     * pattern fits the cache abstraction. {@link com.forumx.redis.gateway.RedisGateway} is
     * reserved for explicit Redis operations — such as counters, distributed locks, session
     * storage, and pub/sub — that are not well served by the cache abstraction.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        RedisObjectSerializer.createSerializer()
                );

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(redisProperties.getCache().getTtl())
                .disableCachingNullValues()
                .computePrefixWith(name ->
                        redisProperties.getCache().getKeyPrefix() + name + "::")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(valueSerializer);

        return RedisCacheManager.builder(factory)
                .cacheDefaults(cacheConfig)
                .transactionAware()
                .build();
    }

    // -------------------------------------------------------------------------
    // Pub/Sub Listener Container
    // -------------------------------------------------------------------------

    /**
     * {@link RedisMessageListenerContainer} that auto-registers every
     * {@link RedisSubscriber} bean present in the Spring context.
     *
     * <p>Adding a new subscriber to a future module requires only:
     * <ol>
     *   <li>Implementing {@link RedisSubscriber} and annotating with {@code @Component}.</li>
     *   <li>Returning the target channel from {@link RedisSubscriber#getChannel()}.</li>
     * </ol>
     * No changes to this class are needed.
     *
     * @param factory     connection factory
     * @param subscribers all {@link RedisSubscriber} beans in the context (empty list if none)
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            List<RedisSubscriber> subscribers) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        subscribers.forEach(subscriber ->
                container.addMessageListener(subscriber, new ChannelTopic(subscriber.getChannel()))
        );
        return container;
    }
}

package com.forumx.presence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.presence.dto.PresenceStatus;
import com.forumx.presence.dto.UserPresence;
import com.forumx.redis.constant.RedisKeys;
import com.forumx.redis.gateway.RedisGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = {
        "forumx.redis.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
public class PresenceRedisIntegrationTest {

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean(name = "redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private PresenceService presenceService;

    @MockBean
    private RedisGateway redisGateway;

    private UserPresence samplePresence;

    @BeforeEach
    public void setUp() {
        samplePresence = UserPresence.builder()
                .userId(99L)
                .username("redis_user")
                .tenantId(1L)
                .status(PresenceStatus.ONLINE)
                .activeSessions(1)
                .build();
    }

    @Test
    public void testRedisReadWriteIntegration() {
        when(redisGateway.get(eq(RedisKeys.presence(99L)), eq(UserPresence.class)))
                .thenReturn(Optional.of(samplePresence));
        when(redisGateway.sCard(eq(RedisKeys.presenceSessions(99L)))).thenReturn(1L);

        presenceService.markOnline(99L, "redis_user", 1L, "session-redis-1");

        Optional<UserPresence> presenceOpt = presenceService.getPresence(99L);
        assertTrue(presenceOpt.isPresent());
        UserPresence presence = presenceOpt.get();
        assertEquals("redis_user", presence.getUsername());
        assertEquals(PresenceStatus.ONLINE, presence.getStatus());

        verify(redisGateway, times(1)).put(eq(RedisKeys.presence(99L)), any(), any());
    }
}

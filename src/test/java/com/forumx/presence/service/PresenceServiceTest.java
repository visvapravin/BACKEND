package com.forumx.presence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.presence.config.PresenceProperties;
import com.forumx.presence.dto.PresenceStatus;
import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.service.impl.PresenceServiceImpl;
import com.forumx.redis.gateway.RedisGateway;
import com.forumx.redis.pubsub.RedisPublisher;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class PresenceServiceTest {

    private RedisGateway redisGateway;
    private PresenceProperties properties;
    private RedisPublisher redisPublisher;
    private PresenceService presenceService;

    @BeforeEach
    public void setUp() {
        redisGateway = mock(RedisGateway.class);
        properties = new PresenceProperties();
        properties.setEnabled(true);
        properties.setTtl(Duration.ofMinutes(5));
        properties.setBroadcastEnabled(true);

        redisPublisher = mock(RedisPublisher.class);

        PresenceServiceImpl impl = new PresenceServiceImpl(redisGateway, properties);
        try {
            java.lang.reflect.Field field = PresenceServiceImpl.class.getDeclaredField("redisPublisher");
            field.setAccessible(true);
            field.set(impl, redisPublisher);
        } catch (Exception e) {
            fail("Reflection setup failed");
        }
        presenceService = impl;
    }

    @Test
    public void testFirstSessionMarkOnline() {
        when(redisGateway.sAdd(eq("forumx:presence:sessions:1"), eq("session-A"))).thenReturn(1L);
        when(redisGateway.sCard(eq("forumx:presence:sessions:1"))).thenReturn(1L);

        presenceService.markOnline(1L, "user1", 10L, "session-A");

        ArgumentCaptor<UserPresence> presenceCaptor = ArgumentCaptor.forClass(UserPresence.class);
        verify(redisGateway, times(1)).put(eq("forumx:presence:user:1"), presenceCaptor.capture(), eq(Duration.ofMinutes(5)));

        UserPresence presence = presenceCaptor.getValue();
        assertNotNull(presence);
        assertEquals(PresenceStatus.ONLINE, presence.getStatus());
        assertEquals(1, presence.getActiveSessions());

        verify(redisPublisher, times(1)).publish(eq(PresenceServiceImpl.PRESENCE_CHANNEL), any());
    }

    @Test
    public void testSecondSessionIncrementsCount() {
        when(redisGateway.sAdd(eq("forumx:presence:sessions:1"), eq("session-B"))).thenReturn(1L);
        when(redisGateway.sCard(eq("forumx:presence:sessions:1"))).thenReturn(2L);

        presenceService.markOnline(1L, "user1", 10L, "session-B");

        ArgumentCaptor<UserPresence> presenceCaptor = ArgumentCaptor.forClass(UserPresence.class);
        verify(redisGateway, times(1)).put(eq("forumx:presence:user:1"), presenceCaptor.capture(), eq(Duration.ofMinutes(5)));

        UserPresence presence = presenceCaptor.getValue();
        assertEquals(2, presence.getActiveSessions());
    }

    @Test
    public void testDisconnectOneSessionStillOnline() {
        UserPresence existing = UserPresence.builder()
                .userId(1L)
                .username("user1")
                .tenantId(10L)
                .status(PresenceStatus.ONLINE)
                .build();
        when(redisGateway.get(eq("forumx:presence:user:1"), eq(UserPresence.class))).thenReturn(Optional.of(existing));
        when(redisGateway.sCard(eq("forumx:presence:sessions:1"))).thenReturn(1L);

        presenceService.markOffline(1L, "session-A");

        ArgumentCaptor<UserPresence> presenceCaptor = ArgumentCaptor.forClass(UserPresence.class);
        verify(redisGateway, times(1)).put(eq("forumx:presence:user:1"), presenceCaptor.capture(), eq(Duration.ofMinutes(5)));

        UserPresence presence = presenceCaptor.getValue();
        assertEquals(PresenceStatus.ONLINE, presence.getStatus());
        assertEquals(1, presence.getActiveSessions());
    }

    @Test
    public void testLastSessionOffline() {
        UserPresence existing = UserPresence.builder()
                .userId(1L)
                .username("user1")
                .tenantId(10L)
                .status(PresenceStatus.ONLINE)
                .build();
        when(redisGateway.get(eq("forumx:presence:user:1"), eq(UserPresence.class))).thenReturn(Optional.of(existing));
        when(redisGateway.sCard(eq("forumx:presence:sessions:1"))).thenReturn(0L);

        presenceService.markOffline(1L, "session-B");

        ArgumentCaptor<UserPresence> presenceCaptor = ArgumentCaptor.forClass(UserPresence.class);
        verify(redisGateway, times(1)).put(eq("forumx:presence:user:1"), presenceCaptor.capture(), eq(Duration.ofMinutes(5)));
        verify(redisGateway, times(1)).delete(eq("forumx:presence:sessions:1"));

        UserPresence presence = presenceCaptor.getValue();
        assertEquals(PresenceStatus.OFFLINE, presence.getStatus());
        assertEquals(0, presence.getActiveSessions());
    }

    @Test
    public void testBatchQuery() {
        UserPresence p1 = UserPresence.builder().userId(1L).username("user1").build();
        UserPresence p2 = UserPresence.builder().userId(2L).username("user2").build();
        when(redisGateway.get(eq("forumx:presence:user:1"), eq(UserPresence.class))).thenReturn(Optional.of(p1));
        when(redisGateway.get(eq("forumx:presence:user:2"), eq(UserPresence.class))).thenReturn(Optional.of(p2));
        when(redisGateway.sCard(eq("forumx:presence:sessions:1"))).thenReturn(1L);
        when(redisGateway.sCard(eq("forumx:presence:sessions:2"))).thenReturn(0L);

        Map<Long, UserPresence> res = presenceService.getPresence(java.util.List.of(1L, 2L));
        assertEquals(2, res.size());
        assertEquals(PresenceStatus.ONLINE, res.get(1L).getStatus());
        assertEquals(1, res.get(1L).getActiveSessions());
        assertEquals(PresenceStatus.OFFLINE, res.get(2L).getStatus());
        assertEquals(0, res.get(2L).getActiveSessions());
    }
}

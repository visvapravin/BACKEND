package com.forumx.presence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.presence.dto.PresenceStatus;
import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.event.PresenceChangedEvent;
import com.forumx.presence.listener.PresenceEventSubscriber;
import com.forumx.redis.pubsub.RedisPublisher;
import com.forumx.websocket.gateway.RealtimeGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = {
        "forumx.redis.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
public class PresenceRealtimeIntegrationTest {

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean(name = "redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private PresenceEventSubscriber presenceEventSubscriber;

    @MockBean
    private RealtimeGateway realtimeGateway;

    @MockBean
    private RedisPublisher redisPublisher;

    @Test
    public void testPresenceServiceTriggersPubSubToRealtimeGateway() throws Exception {
        PresenceChangedEvent eventPayload = new PresenceChangedEvent(
                88L,
                "e2e_presence_user",
                1L,
                PresenceStatus.ONLINE,
                1,
                Instant.now()
        );

        com.forumx.redis.pubsub.RedisEvent<PresenceChangedEvent> redisEvent =
                com.forumx.redis.pubsub.RedisEvent.of("PRESENCE_CHANGED", eventPayload);
        
        String json = com.forumx.redis.serializer.RedisObjectSerializer.createObjectMapper().writeValueAsString(redisEvent);
        Message message = new DefaultMessage("forumx:channel:presence".getBytes(StandardCharsets.UTF_8), json.getBytes(StandardCharsets.UTF_8));

        presenceEventSubscriber.onMessage(message, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.forumx.websocket.dto.RealtimeEvent<UserPresence>> eventCaptor = ArgumentCaptor.forClass(com.forumx.websocket.dto.RealtimeEvent.class);
        verify(realtimeGateway, times(1)).sendToTopic(
                eq("/topic/presence"),
                eventCaptor.capture()
        );

        var captured = eventCaptor.getValue();
        assertNotNull(captured);
        assertEquals("PRESENCE_CHANGED", captured.getType());
        
        UserPresence payload = (UserPresence) captured.getPayload();
        assertEquals("e2e_presence_user", payload.getUsername());
        assertEquals(PresenceStatus.ONLINE, payload.getStatus());
    }
}

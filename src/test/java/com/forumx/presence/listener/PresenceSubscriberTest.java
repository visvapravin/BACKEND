package com.forumx.presence.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.presence.dto.PresenceStatus;
import com.forumx.presence.dto.UserPresence;
import com.forumx.presence.event.PresenceChangedEvent;
import com.forumx.presence.mapper.PresenceRealtimeMapper;
import com.forumx.redis.pubsub.RedisEvent;
import com.forumx.redis.serializer.RedisObjectSerializer;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class PresenceSubscriberTest {

    private RealtimeGateway realtimeGateway;
    private PresenceRealtimeMapper realtimeMapper;
    private PresenceEventSubscriber subscriber;
    private final ObjectMapper mapper = RedisObjectSerializer.createObjectMapper();

    @BeforeEach
    public void setUp() {
        realtimeGateway = mock(RealtimeGateway.class);
        realtimeMapper = new PresenceRealtimeMapper();
        subscriber = new PresenceEventSubscriber(realtimeMapper, realtimeGateway);
    }

    @Test
    public void testOnMessage_SendsToTopic() throws Exception {
        PresenceChangedEvent payload = new PresenceChangedEvent(
                12L,
                "testuser",
                1L,
                PresenceStatus.ONLINE,
                1,
                Instant.now()
        );

        RedisEvent<PresenceChangedEvent> redisEvent = RedisEvent.of("PRESENCE_CHANGED", payload);
        String json = mapper.writeValueAsString(redisEvent);

        Message message = new DefaultMessage("forumx:channel:presence".getBytes(StandardCharsets.UTF_8), json.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<UserPresence>> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway, times(1)).sendToTopic(
                eq("/topic/presence"),
                eventCaptor.capture()
        );

        RealtimeEvent<UserPresence> captured = eventCaptor.getValue();
        assertNotNull(captured);
        assertEquals("PRESENCE_CHANGED", captured.getType());
        assertEquals("testuser", captured.getPayload().getUsername());
        assertEquals(PresenceStatus.ONLINE, captured.getPayload().getStatus());
    }
}

package com.forumx.notification.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.notification.dto.response.NotificationResponse;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.notification.mapper.NotificationRealtimeMapper;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class NotificationSubscriberTest {

    private RealtimeGateway realtimeGateway;
    private NotificationRealtimeMapper realtimeMapper;
    private NotificationEventSubscriber subscriber;

    @BeforeEach
    public void setUp() {
        realtimeGateway = mock(RealtimeGateway.class);
        realtimeMapper = new NotificationRealtimeMapper();
        subscriber = new NotificationEventSubscriber(realtimeMapper, realtimeGateway);
    }

    @Test
    public void testOnNotificationCreated_Success() {
        NotificationCreatedEvent payload = new NotificationCreatedEvent(
                123L,
                456L,
                "testuser",
                1L,
                "ANSWER_CREATED",
                "New Answer",
                "A new reply",
                789L,
                "QUESTION",
                101112L,
                Instant.now()
        );

        EventEnvelope<NotificationCreatedEvent> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                "NOTIFICATION_CREATED",
                1L,
                Instant.now(),
                "notification-service",
                payload
        );

        subscriber.onNotificationCreated(envelope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<NotificationResponse>> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway, times(1)).sendToUser(
                eq("testuser"),
                eq("/queue/notifications"),
                eventCaptor.capture()
        );

        RealtimeEvent<NotificationResponse> capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("NOTIFICATION_CREATED", capturedEvent.getType());
        
        NotificationResponse response = capturedEvent.getPayload();
        assertNotNull(response);
        assertEquals(123L, response.getId());
        assertEquals(456L, response.getRecipientId());
        assertEquals(789L, response.getActorId());
        assertEquals("New Answer", response.getTitle());
        assertEquals("A new reply", response.getMessage());
    }
}

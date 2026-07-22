package com.forumx.support.queue.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.support.queue.event.SupportTicketClaimedEvent;
import com.forumx.support.queue.event.SupportTicketQueuedEvent;
import com.forumx.support.queue.mapper.SupportQueueRealtimeMapper;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SupportQueueSubscriberTest {

    @Mock
    private RealtimeGateway realtimeGateway;

    private SupportQueueRealtimeMapper mapper;
    private SupportQueueEventSubscriber subscriber;

    @BeforeEach
    public void setUp() {
        mapper = new SupportQueueRealtimeMapper();
        subscriber = new SupportQueueEventSubscriber(mapper, realtimeGateway);
    }

    @Test
    public void testOnSupportQueueEventQueued() {
        SupportTicketQueuedEvent payload = new SupportTicketQueuedEvent(
                101L, 1L, 5L, "userA", "Help needed", "HIGH", "OPEN", Instant.now()
        );
        EventEnvelope<SupportTicketQueuedEvent> envelope = EventEnvelope.of(
                "SUPPORT_TICKET_QUEUED", 1L, "support-service", payload
        );

        subscriber.onSupportQueueEvent(envelope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<SupportTicketQueuedEvent>> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway).sendToTopic(eq("/topic/support/queue"), captor.capture());

        RealtimeEvent<SupportTicketQueuedEvent> realtimeEvent = captor.getValue();
        assertNotNull(realtimeEvent);
        assertEquals("SUPPORT_TICKET_QUEUED", realtimeEvent.getType());
        assertEquals(payload, realtimeEvent.getPayload());
    }

    @Test
    public void testOnSupportQueueEventClaimed() {
        SupportTicketClaimedEvent payload = new SupportTicketClaimedEvent(
                101L, 1L, 20L, "modAgent", Instant.now()
        );
        EventEnvelope<SupportTicketClaimedEvent> envelope = EventEnvelope.of(
                "SUPPORT_TICKET_CLAIMED", 1L, "support-service", payload
        );

        subscriber.onSupportQueueEvent(envelope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<SupportTicketClaimedEvent>> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway).sendToTopic(eq("/topic/support/queue"), captor.capture());

        RealtimeEvent<SupportTicketClaimedEvent> realtimeEvent = captor.getValue();
        assertNotNull(realtimeEvent);
        assertEquals("SUPPORT_TICKET_CLAIMED", realtimeEvent.getType());
        assertEquals(payload, realtimeEvent.getPayload());
    }
}

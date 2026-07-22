package com.forumx.support.queue.integration;

import static org.mockito.Mockito.*;

import java.time.Instant;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.support.queue.event.SupportTicketQueuedEvent;
import com.forumx.support.queue.listener.SupportQueueEventSubscriber;
import com.forumx.support.queue.mapper.SupportQueueRealtimeMapper;
import com.forumx.websocket.gateway.RealtimeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SupportQueueRealtimeDeliveryTest {

    private RealtimeGateway realtimeGateway;
    private SupportQueueEventSubscriber subscriber;

    @BeforeEach
    public void setUp() {
        realtimeGateway = mock(RealtimeGateway.class);
        SupportQueueRealtimeMapper mapper = new SupportQueueRealtimeMapper();
        subscriber = new SupportQueueEventSubscriber(mapper, realtimeGateway);
    }

    @Test
    public void testDirectTopicBroadcastDelivery() {
        SupportTicketQueuedEvent payload = new SupportTicketQueuedEvent(
                999L, 1L, 10L, "test_user", "Delivery Test", "NORMAL", "OPEN", Instant.now()
        );
        EventEnvelope<SupportTicketQueuedEvent> envelope = EventEnvelope.of(
                "SUPPORT_TICKET_QUEUED", 1L, "support-service", payload
        );

        subscriber.onSupportQueueEvent(envelope);

        verify(realtimeGateway, times(1)).sendToTopic(
                eq("/topic/support/queue"),
                argThat(event -> event.getType().equals("SUPPORT_TICKET_QUEUED") && event.getPayload().equals(payload))
        );
    }
}

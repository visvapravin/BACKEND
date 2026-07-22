package com.forumx.support.queue.publisher;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.support.queue.event.SupportTicketClaimedEvent;
import com.forumx.support.queue.event.SupportTicketQueuedEvent;
import com.forumx.support.queue.event.SupportTicketStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SupportQueuePublisherTest {

    @Mock
    private EventGateway eventGateway;

    private SupportQueueEventPublisher publisher;

    @BeforeEach
    public void setUp() {
        publisher = new SupportQueueEventPublisher(eventGateway);
    }

    @Test
    public void testHandleTicketQueued() {
        SupportTicketQueuedEvent event = new SupportTicketQueuedEvent(
                100L, 1L, 10L, "user1", "Subject", "HIGH", "OPEN", Instant.now()
        );

        publisher.handleTicketQueued(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<SupportTicketQueuedEvent>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway).publish(
                eq(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.SUPPORT_QUEUED),
                captor.capture()
        );

        EventEnvelope<SupportTicketQueuedEvent> envelope = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("SUPPORT_TICKET_QUEUED", envelope.eventType());
        org.junit.jupiter.api.Assertions.assertEquals(1L, envelope.tenantId());
        org.junit.jupiter.api.Assertions.assertEquals(event, envelope.payload());
    }

    @Test
    public void testHandleTicketClaimed() {
        SupportTicketClaimedEvent event = new SupportTicketClaimedEvent(
                100L, 1L, 20L, "mod1", Instant.now()
        );

        publisher.handleTicketClaimed(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<SupportTicketClaimedEvent>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway).publish(
                eq(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.SUPPORT_CLAIMED),
                captor.capture()
        );

        EventEnvelope<SupportTicketClaimedEvent> envelope = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("SUPPORT_TICKET_CLAIMED", envelope.eventType());
        org.junit.jupiter.api.Assertions.assertEquals(event, envelope.payload());
    }

    @Test
    public void testHandleTicketStatusChanged() {
        SupportTicketStatusChangedEvent event = new SupportTicketStatusChangedEvent(
                100L, 1L, "OPEN", "RESOLVED", 20L, Instant.now()
        );

        publisher.handleTicketStatusChanged(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<SupportTicketStatusChangedEvent>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway).publish(
                eq(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.SUPPORT_STATUS_CHANGED),
                captor.capture()
        );

        EventEnvelope<SupportTicketStatusChangedEvent> envelope = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("SUPPORT_TICKET_STATUS_CHANGED", envelope.eventType());
        org.junit.jupiter.api.Assertions.assertEquals(event, envelope.payload());
    }
}

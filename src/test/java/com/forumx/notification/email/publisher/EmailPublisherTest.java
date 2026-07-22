package com.forumx.notification.email.publisher;

import static org.mockito.Mockito.*;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.event.EmailFailedEvent;
import com.forumx.notification.email.event.EmailSendRequestedEvent;
import com.forumx.notification.email.event.EmailSentEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EmailPublisherTest {

    private EventGateway eventGateway;
    private EmailEventPublisher publisher;

    @BeforeEach
    public void setUp() {
        eventGateway = mock(EventGateway.class);
        publisher = new EmailEventPublisher(eventGateway);
    }

    @Test
    public void testHandleSendRequested() {
        EmailSendRequestedEvent event = new EmailSendRequestedEvent(
                UUID.randomUUID(),
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.GENERIC,
                new HashMap<>(),
                Instant.now()
        );

        publisher.handleSendRequested(event);

        verify(eventGateway).publish(
                eq(MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(MessagingRoutingKeys.EMAIL_SEND),
                any(EventEnvelope.class)
        );
    }

    @Test
    public void testHandleSent() {
        EmailSentEvent event = new EmailSentEvent(
                UUID.randomUUID(),
                1L,
                "test@forumx.com",
                Instant.now()
        );

        publisher.handleSent(event);

        verify(eventGateway).publish(
                eq(MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(MessagingRoutingKeys.EMAIL_SENT),
                any(EventEnvelope.class)
        );
    }

    @Test
    public void testHandleFailed() {
        EmailFailedEvent event = new EmailFailedEvent(
                UUID.randomUUID(),
                1L,
                "test@forumx.com",
                "SMTP Error",
                Instant.now()
        );

        publisher.handleFailed(event);

        verify(eventGateway).publish(
                eq(MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(MessagingRoutingKeys.EMAIL_FAILED),
                any(EventEnvelope.class)
        );
    }
}

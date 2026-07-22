package com.forumx.notification.email.listener;

import static org.mockito.Mockito.*;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.event.EmailSendRequestedEvent;
import com.forumx.notification.email.service.EmailService;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EmailSubscriberTest {

    private EmailService emailService;
    private EmailEventSubscriber subscriber;

    @BeforeEach
    public void setUp() {
        emailService = mock(EmailService.class);
        subscriber = new EmailEventSubscriber(emailService);
    }

    @Test
    public void testOnEmailSendEvent() {
        EmailSendRequestedEvent payload = new EmailSendRequestedEvent(
                UUID.randomUUID(),
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.GENERIC,
                new HashMap<>(),
                Instant.now()
        );

        EventEnvelope<EmailSendRequestedEvent> envelope = EventEnvelope.of(
                "EMAIL_SEND_REQUESTED",
                1L,
                "test",
                payload
        );

        subscriber.onEmailSendEvent(envelope);

        verify(emailService).processAndSendEmail(
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.GENERIC,
                payload.templateModel()
        );
    }
}

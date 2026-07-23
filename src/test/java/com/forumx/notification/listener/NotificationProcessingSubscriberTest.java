package com.forumx.notification.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.notification.service.NotificationApplicationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NotificationProcessingSubscriberTest {

    private NotificationApplicationService notificationApplicationService;
    private NotificationProcessingSubscriber subscriber;

    @BeforeEach
    public void setUp() {
        notificationApplicationService = mock(NotificationApplicationService.class);
        subscriber = new NotificationProcessingSubscriber(notificationApplicationService);
    }

    @Test
    public void testQueueName() {
        assertEquals("forumx.user.notification.queue", subscriber.queueName());
    }

    @Test
    public void testOnNotificationCreated_DelegatesToApplicationService() {
        NotificationCreatedEvent payload = new NotificationCreatedEvent(
                100L,
                200L,
                "johndoe",
                1L,
                "QUESTION_COMMENT_CREATED",
                "New Comment",
                "Someone commented on your question",
                300L,
                "QUESTION",
                400L,
                Instant.now()
        );

        EventEnvelope<NotificationCreatedEvent> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                "NOTIFICATION_CREATED",
                1L,
                Instant.now(),
                "test-source",
                payload
        );

        subscriber.onNotificationCreated(envelope);

        verify(notificationApplicationService, times(1)).processNotificationCreated(payload);
    }
}

package com.forumx.notification.publisher;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.notification.event.NotificationCreatedSpringEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class NotificationEventPublisher {

    private final EventGateway eventGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationCreated(NotificationCreatedSpringEvent springEvent) {
        NotificationCreatedEvent event = springEvent.event();
        log.info("Handling transactional event for notification creation. notificationId={}, recipientUserId={}, tenantId={}",
                event.notificationId(), event.recipientUserId(), event.tenantId());

        try {
            EventEnvelope<NotificationCreatedEvent> envelope = EventEnvelope.of(
                    "NOTIFICATION_CREATED",
                    event.tenantId(),
                    "notification-service",
                    event
            );

            log.info("Publishing notification event to RabbitMQ. eventId={}, notificationId={}, tenantId={}, recipientUserId={}",
                    envelope.eventId(), event.notificationId(), event.tenantId(), event.recipientUserId());

            eventGateway.publish(
                    MessagingExchanges.USER_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.NOTIFICATION_CREATED,
                    envelope
            );

            log.info("Successfully published notification event to RabbitMQ. eventId={}, notificationId={}",
                    envelope.eventId(), event.notificationId());
        } catch (Exception e) {
            log.error("Failed to publish NotificationCreatedEvent to RabbitMQ. notificationId={}, error={}",
                    event.notificationId(), e.getMessage(), e);
        }
    }
}

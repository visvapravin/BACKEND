package com.forumx.notification.email.publisher;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.notification.email.event.EmailFailedEvent;
import com.forumx.notification.email.event.EmailSendRequestedEvent;
import com.forumx.notification.email.event.EmailSentEvent;
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
public class EmailEventPublisher {

    private final EventGateway eventGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSendRequested(EmailSendRequestedEvent event) {
        log.info("Handling EmailSendRequestedEvent transactional event. eventId={}, recipient={}",
                event.eventId(), event.recipientEmail());
        try {
            EventEnvelope<EmailSendRequestedEvent> envelope = EventEnvelope.of(
                    "EMAIL_SEND_REQUESTED",
                    event.tenantId(),
                    "notification-service",
                    event
            );

            eventGateway.publish(
                    MessagingExchanges.NOTIFICATION_EXCHANGE,
                    MessagingRoutingKeys.EMAIL_SEND,
                    envelope
            );
            log.info("Published EMAIL_SEND_REQUESTED to RabbitMQ. eventId={}", event.eventId());
        } catch (Exception e) {
            log.error("Failed to publish EmailSendRequestedEvent to RabbitMQ. eventId={}, error={}",
                    event.eventId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSent(EmailSentEvent event) {
        log.info("Handling EmailSentEvent transactional event. eventId={}, recipient={}",
                event.eventId(), event.recipientEmail());
        try {
            EventEnvelope<EmailSentEvent> envelope = EventEnvelope.of(
                    "EMAIL_SENT",
                    event.tenantId(),
                    "notification-service",
                    event
            );

            eventGateway.publish(
                    MessagingExchanges.NOTIFICATION_EXCHANGE,
                    MessagingRoutingKeys.EMAIL_SENT,
                    envelope
            );
            log.info("Published EMAIL_SENT to RabbitMQ. eventId={}", event.eventId());
        } catch (Exception e) {
            log.error("Failed to publish EmailSentEvent to RabbitMQ. eventId={}, error={}",
                    event.eventId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFailed(EmailFailedEvent event) {
        log.info("Handling EmailFailedEvent transactional event. eventId={}, recipient={}",
                event.eventId(), event.recipientEmail());
        try {
            EventEnvelope<EmailFailedEvent> envelope = EventEnvelope.of(
                    "EMAIL_FAILED",
                    event.tenantId(),
                    "notification-service",
                    event
            );

            eventGateway.publish(
                    MessagingExchanges.NOTIFICATION_EXCHANGE,
                    MessagingRoutingKeys.EMAIL_FAILED,
                    envelope
            );
            log.info("Published EMAIL_FAILED to RabbitMQ. eventId={}", event.eventId());
        } catch (Exception e) {
            log.error("Failed to publish EmailFailedEvent to RabbitMQ. eventId={}, error={}",
                    event.eventId(), e.getMessage(), e);
        }
    }
}

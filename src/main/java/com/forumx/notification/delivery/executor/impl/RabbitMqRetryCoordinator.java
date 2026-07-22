package com.forumx.notification.delivery.executor.impl;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.dto.message.NotificationDeadLetterPayload;
import com.forumx.notification.delivery.dto.message.NotificationRetryPayload;
import com.forumx.notification.delivery.executor.RetryCoordinator;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqRetryCoordinator implements RetryCoordinator {

    private final EventGateway eventGateway;

    @Override
    public void scheduleRetry(NotificationDeliveryContext context, Duration delay) {
        Long notifId = context.notification() != null ? context.notification().getId() : null;
        int attemptNum = context.currentAttempt() != null ? context.currentAttempt().getAttemptNumber() : 1;

        NotificationRetryPayload payload = new NotificationRetryPayload(
                notifId,
                context.channel(),
                attemptNum,
                context.correlationId()
        );

        EventEnvelope<NotificationRetryPayload> envelope = EventEnvelope.of(
                "NOTIFICATION_RETRY_SCHEDULED",
                context.tenant() != null ? context.tenant().getId() : null,
                "notification-delivery-service",
                payload
        );

        log.info("Scheduling delivery retry for channel {} attempt {} correlationId {} with delay {}s",
                context.channel(), attemptNum, context.correlationId(), delay.getSeconds());

        eventGateway.publish(
                com.forumx.messaging.constant.MessagingExchanges.NOTIFICATION_EXCHANGE,
                "notification.delivery.retry",
                envelope
        );
    }

    @Override
    public void sendToDeadLetter(NotificationDeliveryContext context, String reason) {
        Long notifId = context.notification() != null ? context.notification().getId() : null;
        int attemptNum = context.currentAttempt() != null ? context.currentAttempt().getAttemptNumber() : 1;

        NotificationDeadLetterPayload payload = new NotificationDeadLetterPayload(
                notifId,
                attemptNum,
                context.channel(),
                reason,
                context.correlationId(),
                context
        );

        EventEnvelope<NotificationDeadLetterPayload> envelope = EventEnvelope.of(
                "NOTIFICATION_DEAD_LETTER",
                context.tenant() != null ? context.tenant().getId() : null,
                "notification-delivery-service",
                payload
        );

        log.warn("Routing delivery failure to DLQ for channel {} attempt {} correlationId {}. Reason: {}",
                context.channel(), attemptNum, context.correlationId(), reason);

        eventGateway.publish(
                com.forumx.messaging.constant.MessagingExchanges.NOTIFICATION_EXCHANGE,
                "notification.delivery.dead",
                envelope
        );
    }
}

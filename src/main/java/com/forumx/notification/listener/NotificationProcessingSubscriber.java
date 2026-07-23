package com.forumx.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import com.forumx.messaging.subscriber.EventSubscriber;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.notification.service.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Zero-logic RabbitMQ subscriber responsible purely for receiving NotificationCreatedEvent messages
 * and delegating execution directly to NotificationApplicationService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class NotificationProcessingSubscriber implements EventSubscriber {

    private final NotificationApplicationService notificationApplicationService;
    private final ObjectMapper objectMapper = MessageSerializer.objectMapper();

    @Override
    public String queueName() {
        return "forumx.user.notification.queue";
    }

    @RabbitListener(queues = "${forumx.rabbitmq.queues.user-notification:forumx.user.notification.queue}")
    public void onNotificationCreated(EventEnvelope<?> envelope) {
        log.info("[NotificationProcessingSubscriber] Received notification event envelope. eventId={}, tenantId={}",
                envelope.eventId(), envelope.tenantId());

        NotificationCreatedEvent event;
        if (envelope.payload() instanceof NotificationCreatedEvent) {
            event = (NotificationCreatedEvent) envelope.payload();
        } else {
            event = objectMapper.convertValue(envelope.payload(), NotificationCreatedEvent.class);
        }

        // Delegation only - zero business logic in subscriber
        notificationApplicationService.processNotificationCreated(event);
    }
}

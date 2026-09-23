package com.forumx.notification.listener;

import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.subscriber.EventSubscriber;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.notification.mapper.NotificationRealtimeMapper;
import com.forumx.websocket.gateway.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Deprecated(forRemoval = true)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "false")
public class NotificationEventSubscriber implements EventSubscriber {

    private final NotificationRealtimeMapper realtimeMapper;
    private final RealtimeGateway realtimeGateway;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = com.forumx.messaging.serializer.MessageSerializer.objectMapper();

    @Deprecated(forRemoval = true)
    @Override
    public String queueName() {
        return MessagingQueues.USER_NOTIFICATION_QUEUE;
    }

    @Deprecated(forRemoval = true)
    @RabbitListener(queues = MessagingQueues.USER_NOTIFICATION_QUEUE)
    public void onNotificationCreated(EventEnvelope<?> envelope) {
        log.info("Received notification created event. eventId={}, tenantId={}", envelope.eventId(), envelope.tenantId());

        NotificationCreatedEvent event;
        if (envelope.payload() instanceof NotificationCreatedEvent) {
            event = (NotificationCreatedEvent) envelope.payload();
        } else {
            event = objectMapper.convertValue(envelope.payload(), NotificationCreatedEvent.class);
        }

        log.info("Processing notification event. eventId={}, notificationId={}, recipientUserId={}, recipientUsername={}",
                envelope.eventId(), event.notificationId(), event.recipientUserId(), event.recipientUsername());

        try {
            var realtimeEvent = realtimeMapper.toRealtimeEvent(event);

            log.info("Delivering WebSocket notification. notificationId={}, recipientUsername={}, eventId={}, routingKey={}",
                    event.notificationId(), event.recipientUsername(), envelope.eventId(), MessagingRoutingKeys.NOTIFICATION_CREATED);

            realtimeGateway.sendToUser(
                    event.recipientUsername(),
                    "/queue/notifications",
                    realtimeEvent
            );

            log.info("Successfully delivered WebSocket notification. notificationId={}, recipientUsername={}",
                    event.notificationId(), event.recipientUsername());
        } catch (Exception e) {
            log.error("Failed to deliver WebSocket notification. eventId={}, notificationId={}, recipientUsername={}, error={}",
                    envelope.eventId(), event.notificationId(), event.recipientUsername(), e.getMessage(), e);
            throw e;
        }
    }
}

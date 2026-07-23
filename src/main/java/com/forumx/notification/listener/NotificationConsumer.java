package com.forumx.notification.listener;

import com.forumx.notification.dispatcher.NotificationDeliveryRequest;
import com.forumx.notification.dispatcher.NotificationDispatcher;
import com.forumx.notification.dto.NotificationEvent;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.service.NotificationApplicationService;
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
public class NotificationConsumer {

    private final NotificationApplicationService notificationApplicationService;
    private final NotificationDispatcher notificationDispatcher;

    @RabbitListener(queues = "forumx.notification.queue")
    public void consume(NotificationEvent event) {
        log.info("Received notification event for recipient {}", event.email());
        Notification persistedNotification = null;
        try {
            persistedNotification = notificationApplicationService.saveFromEvent(event);
        } catch (Exception e) {
            log.error("Failed to persist notification for event {}: {}", event.notificationId(), e.getMessage(), e);
        }

        notificationDispatcher.dispatch(new NotificationDeliveryRequest(persistedNotification, event));
    }
}

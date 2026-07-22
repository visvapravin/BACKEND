package com.forumx.notification.delivery.messaging;

import com.forumx.messaging.config.MessagingConfig;
import com.forumx.notification.delivery.dto.NotificationDeadLetterEvent;
import com.forumx.notification.delivery.dto.NotificationRetryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class NotificationRetryPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishRetry(NotificationRetryEvent event, long delayMs) {
        log.info("[RetryPublisher] Publishing retry event for notificationId={}, channel={}, attempt={}, delayMs={}, correlationId={}",
                event.notificationId(), event.channel(), event.attemptNumber(), delayMs, event.correlationId());
        
        rabbitTemplate.convertAndSend(
                MessagingConfig.RETRY_EXCHANGE,
                MessagingConfig.RETRY_ROUTING_KEY,
                event,
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delayMs));
                    return message;
                }
        );
    }

    public void publishDeadLetter(NotificationDeadLetterEvent event) {
        log.warn("[DeadLetterPublisher] Publishing dead-letter event for notificationId={}, channel={}, totalAttempts={}, correlationId={}",
                event.notificationId(), event.channel(), event.totalAttempts(), event.correlationId());

        rabbitTemplate.convertAndSend(
                MessagingConfig.DLX_EXCHANGE,
                MessagingConfig.DEAD_LETTER_ROUTING_KEY,
                event
        );
    }
}

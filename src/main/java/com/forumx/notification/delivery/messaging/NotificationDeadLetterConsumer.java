package com.forumx.notification.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.notification.delivery.dto.message.NotificationDeadLetterPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class NotificationDeadLetterConsumer {

    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${forumx.rabbitmq.queues.dead-letter:forumx.notification.dead.queue}")
    public void onDeadLetterEvent(EventEnvelope<?> envelope) {
        log.warn("Received dead letter delivery event: eventId={} type={}", envelope.eventId(), envelope.eventType());
        try {
            NotificationDeadLetterPayload payload = objectMapper.convertValue(envelope.payload(), NotificationDeadLetterPayload.class);
            if (payload != null) {
                log.error("DEAD LETTER LOGGED: correlationId={} channel={} attempt={} reason={}",
                        payload.correlationId(), payload.channel(), payload.attemptNumber(), payload.reason());
            }
        } catch (Exception e) {
            log.error("Error processing dead letter event: {}", e.getMessage(), e);
        }
    }
}

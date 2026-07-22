package com.forumx.notification.email.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import com.forumx.messaging.subscriber.EventSubscriber;
import com.forumx.notification.email.event.EmailSendRequestedEvent;
import com.forumx.notification.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class EmailEventSubscriber implements EventSubscriber {

    private final EmailService emailService;
    private final ObjectMapper objectMapper = MessageSerializer.objectMapper();

    @Override
    public String queueName() {
        return MessagingQueues.EMAIL_QUEUE;
    }

    @RabbitListener(queues = MessagingQueues.EMAIL_QUEUE)
    public void onEmailSendEvent(EventEnvelope<?> envelope) {
        log.info("Received email send event from RabbitMQ. eventType={}, eventId={}, tenantId={}",
                envelope.eventType(), envelope.eventId(), envelope.tenantId());

        try {
            EmailSendRequestedEvent sendEvent = parsePayload(envelope);
            emailService.processAndSendEmail(
                    envelope.tenantId(),
                    sendEvent.recipientEmail(),
                    sendEvent.recipientUsername(),
                    sendEvent.templateType(),
                    sendEvent.templateModel()
            );
            log.info("Successfully processed email event. eventId={}", envelope.eventId());
        } catch (Exception e) {
            log.error("Failed to process email send event. eventId={}, error={}",
                    envelope.eventId(), e.getMessage(), e);
            throw e;
        }
    }

    private EmailSendRequestedEvent parsePayload(EventEnvelope<?> envelope) {
        Object rawPayload = envelope.payload();
        if (rawPayload instanceof EmailSendRequestedEvent event) {
            return event;
        }

        String eventType = envelope.eventType();
        if ("EMAIL_SEND_REQUESTED".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, EmailSendRequestedEvent.class);
        } else {
            throw new IllegalArgumentException("Unsupported email eventType: " + eventType);
        }
    }
}

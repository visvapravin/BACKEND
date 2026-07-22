package com.forumx.support.queue.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import com.forumx.messaging.subscriber.EventSubscriber;
import com.forumx.support.queue.event.SupportQueueEvent;
import com.forumx.support.queue.event.SupportTicketClaimedEvent;
import com.forumx.support.queue.event.SupportTicketQueuedEvent;
import com.forumx.support.queue.event.SupportTicketStatusChangedEvent;
import com.forumx.support.queue.mapper.SupportQueueRealtimeMapper;
import com.forumx.websocket.gateway.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class SupportQueueEventSubscriber implements EventSubscriber {

    private final SupportQueueRealtimeMapper realtimeMapper;
    private final RealtimeGateway realtimeGateway;
    private final ObjectMapper objectMapper = MessageSerializer.objectMapper();

    public static final String SUPPORT_QUEUE_TOPIC = "/topic/support/queue";

    @Override
    public String queueName() {
        return MessagingQueues.SUPPORT_QUEUE;
    }

    @RabbitListener(queues = MessagingQueues.SUPPORT_QUEUE)
    public void onSupportQueueEvent(EventEnvelope<?> envelope) {
        log.info("Received support queue event from RabbitMQ. eventType={}, eventId={}, tenantId={}",
                envelope.eventType(), envelope.eventId(), envelope.tenantId());

        try {
            SupportQueueEvent queueEvent = parsePayload(envelope);
            var realtimeEvent = realtimeMapper.toRealtimeEvent(envelope.eventType(), queueEvent);

            log.info("Broadcasting support queue event to WebSocket topic. topic={}, eventType={}, ticketId={}, tenantId={}",
                    SUPPORT_QUEUE_TOPIC, envelope.eventType(), queueEvent.ticketId(), queueEvent.tenantId());

            realtimeGateway.sendToTopic(SUPPORT_QUEUE_TOPIC, realtimeEvent);

            log.info("Successfully broadcast support queue event. ticketId={}, eventType={}",
                    queueEvent.ticketId(), envelope.eventType());
        } catch (Exception e) {
            log.error("Failed to process and broadcast support queue event. eventId={}, eventType={}, error={}",
                    envelope.eventId(), envelope.eventType(), e.getMessage(), e);
            throw e;
        }
    }

    private SupportQueueEvent parsePayload(EventEnvelope<?> envelope) {
        Object rawPayload = envelope.payload();
        if (rawPayload instanceof SupportQueueEvent event) {
            return event;
        }
        
        String eventType = envelope.eventType();
        if ("SUPPORT_TICKET_QUEUED".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, SupportTicketQueuedEvent.class);
        } else if ("SUPPORT_TICKET_CLAIMED".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, SupportTicketClaimedEvent.class);
        } else if ("SUPPORT_TICKET_STATUS_CHANGED".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, SupportTicketStatusChangedEvent.class);
        } else {
            // Default fallback deserialization attempt
            try {
                return objectMapper.convertValue(rawPayload, SupportTicketQueuedEvent.class);
            } catch (Exception e) {
                log.warn("Unknown support queue eventType '{}', converting raw payload", eventType);
                throw new IllegalArgumentException("Unsupported support queue eventType: " + eventType);
            }
        }
    }
}

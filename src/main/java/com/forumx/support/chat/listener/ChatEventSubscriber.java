package com.forumx.support.chat.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import com.forumx.messaging.subscriber.EventSubscriber;
import com.forumx.support.chat.event.durable.ChatEvent;
import com.forumx.support.chat.event.durable.ChatMessageDeletedEvent;
import com.forumx.support.chat.event.durable.ChatMessageReadEvent;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.mapper.ChatRealtimeMapper;
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
public class ChatEventSubscriber implements EventSubscriber {

    private final ChatRealtimeMapper realtimeMapper;
    private final RealtimeGateway realtimeGateway;
    private final ObjectMapper objectMapper = MessageSerializer.objectMapper();

    @Override
    public String queueName() {
        return MessagingQueues.CHAT_QUEUE;
    }

    @RabbitListener(queues = MessagingQueues.CHAT_QUEUE)
    public void onChatEvent(EventEnvelope<?> envelope) {
        log.info("Received chat event from RabbitMQ. eventType={}, eventId={}, tenantId={}",
                envelope.eventType(), envelope.eventId(), envelope.tenantId());

        try {
            ChatEvent chatEvent = parsePayload(envelope);
            var realtimeEvent = realtimeMapper.toRealtimeEvent(envelope.eventType(), chatEvent);

            String destination = "/topic/tenants/" + envelope.tenantId() + "/chat/" + chatEvent.sessionId();
            log.info("Broadcasting chat event to WebSocket topic. topic={}, eventType={}, sessionId={}, tenantId={}",
                    destination, envelope.eventType(), chatEvent.sessionId(), envelope.tenantId());

            realtimeGateway.sendToTopic(destination, realtimeEvent);

            log.info("Successfully broadcast chat event. sessionId={}, eventType={}",
                    chatEvent.sessionId(), envelope.eventType());
        } catch (Exception e) {
            log.error("Failed to process and broadcast chat event. eventId={}, eventType={}, error={}",
                    envelope.eventId(), envelope.eventType(), e.getMessage(), e);
            throw e;
        }
    }

    private ChatEvent parsePayload(EventEnvelope<?> envelope) {
        Object rawPayload = envelope.payload();
        if (rawPayload instanceof ChatEvent event) {
            return event;
        }
        
        String eventType = envelope.eventType();
        if ("CHAT_MESSAGE_SENT".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, ChatMessageSentEvent.class);
        } else if ("CHAT_MESSAGE_DELETED".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, ChatMessageDeletedEvent.class);
        } else if ("CHAT_MESSAGE_READ".equalsIgnoreCase(eventType)) {
            return objectMapper.convertValue(rawPayload, ChatMessageReadEvent.class);
        } else {
            throw new IllegalArgumentException("Unsupported chat eventType: " + eventType);
        }
    }
}

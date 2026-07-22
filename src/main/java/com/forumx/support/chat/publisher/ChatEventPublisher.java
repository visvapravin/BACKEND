package com.forumx.support.chat.publisher;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.support.chat.dto.request.TypingPayload;
import com.forumx.support.chat.event.durable.ChatMessageDeletedEvent;
import com.forumx.support.chat.event.durable.ChatMessageReadEvent;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.event.ephemeral.TypingStartedEvent;
import com.forumx.support.chat.event.ephemeral.TypingStoppedEvent;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final ObjectProvider<EventGateway> eventGatewayProvider;
    private final RealtimeGateway realtimeGateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        log.info("Handling transactional event for chat message sent. messageId={}, tenantId={}", 
                event.messageId(), event.tenantId());
        try {
            EventEnvelope<ChatMessageSentEvent> envelope = EventEnvelope.of(
                    "CHAT_MESSAGE_SENT",
                    event.tenantId(),
                    "support-chat-service",
                    event
            );
            eventGatewayProvider.ifAvailable(gateway -> gateway.publish(
                    MessagingExchanges.SUPPORT_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.CHAT_MESSAGE_SENT,
                    envelope
            ));
            log.info("Published ChatMessageSentEvent to RabbitMQ. eventId={}, messageId={}", 
                    envelope.eventId(), event.messageId());
        } catch (Exception e) {
            log.error("Failed to publish ChatMessageSentEvent. messageId={}, error={}", 
                    event.messageId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(ChatMessageDeletedEvent event) {
        log.info("Handling transactional event for chat message deleted. messageId={}, tenantId={}", 
                event.messageId(), event.tenantId());
        try {
            EventEnvelope<ChatMessageDeletedEvent> envelope = EventEnvelope.of(
                    "CHAT_MESSAGE_DELETED",
                    event.tenantId(),
                    "support-chat-service",
                    event
            );
            eventGatewayProvider.ifAvailable(gateway -> gateway.publish(
                    MessagingExchanges.SUPPORT_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.CHAT_MESSAGE_DELETED,
                    envelope
            ));
            log.info("Published ChatMessageDeletedEvent to RabbitMQ. eventId={}, messageId={}", 
                    envelope.eventId(), event.messageId());
        } catch (Exception e) {
            log.error("Failed to publish ChatMessageDeletedEvent. messageId={}, error={}", 
                    event.messageId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageRead(ChatMessageReadEvent event) {
        log.info("Handling transactional event for chat messages read. sessionId={}, tenantId={}", 
                event.sessionId(), event.tenantId());
        try {
            EventEnvelope<ChatMessageReadEvent> envelope = EventEnvelope.of(
                    "CHAT_MESSAGE_READ",
                    event.tenantId(),
                    "support-chat-service",
                    event
            );
            eventGatewayProvider.ifAvailable(gateway -> gateway.publish(
                    MessagingExchanges.SUPPORT_EVENTS_EXCHANGE,
                    MessagingRoutingKeys.CHAT_MESSAGE_READ,
                    envelope
            ));
            log.info("Published ChatMessageReadEvent to RabbitMQ. eventId={}, sessionId={}", 
                    envelope.eventId(), event.sessionId());
        } catch (Exception e) {
            log.error("Failed to publish ChatMessageReadEvent. sessionId={}, error={}", 
                    event.sessionId(), e.getMessage(), e);
        }
    }

    @EventListener
    public void onTypingStarted(TypingStartedEvent event) {
        log.debug("Handling transient typing started event. sessionId={}, userId={}", 
                event.sessionId(), event.userId());
        RealtimeEvent<TypingPayload> realtimeEvent = RealtimeEvent.<TypingPayload>builder()
                .type("TYPING_STARTED")
                .payload(new TypingPayload("START"))
                .build();
        
        String destination = "/topic/tenants/" + event.tenantId() + "/chat/" + event.sessionId() + "/typing";
        realtimeGateway.sendToTopic(destination, realtimeEvent);
        log.debug("Successfully broadcast transient TYPING_STARTED to topic={}", destination);
    }

    @EventListener
    public void onTypingStopped(TypingStoppedEvent event) {
        log.debug("Handling transient typing stopped event. sessionId={}, userId={}", 
                event.sessionId(), event.userId());
        RealtimeEvent<TypingPayload> realtimeEvent = RealtimeEvent.<TypingPayload>builder()
                .type("TYPING_STOPPED")
                .payload(new TypingPayload("STOP"))
                .build();
        
        String destination = "/topic/tenants/" + event.tenantId() + "/chat/" + event.sessionId() + "/typing";
        realtimeGateway.sendToTopic(destination, realtimeEvent);
        log.debug("Successfully broadcast transient TYPING_STOPPED to topic={}", destination);
    }
}

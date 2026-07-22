package com.forumx.support.chat.publisher;

import static org.mockito.Mockito.*;

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
import com.forumx.websocket.gateway.RealtimeGateway;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
public class ChatPublisherTest {

    @Mock private EventGateway eventGateway;
    @Mock private ObjectProvider<EventGateway> eventGatewayProvider;
    @Mock private RealtimeGateway realtimeGateway;

    private ChatEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<EventGateway> consumer = invocation.getArgument(0);
            consumer.accept(eventGateway);
            return null;
        }).when(eventGatewayProvider).ifAvailable(any(java.util.function.Consumer.class));

        publisher = new ChatEventPublisher(eventGatewayProvider, realtimeGateway);
    }

    @Test
    public void testOnMessageSent() {
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                UUID.randomUUID(), 1L, 200L, 100L, 10L, "customer", 500L, "Hello", "TEXT", Instant.now()
        );

        publisher.onMessageSent(event);

        verify(eventGateway).publish(
                eq(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.CHAT_MESSAGE_SENT),
                any(EventEnvelope.class)
        );
    }

    @Test
    public void testOnMessageDeleted() {
        ChatMessageDeletedEvent event = new ChatMessageDeletedEvent(
                UUID.randomUUID(), 1L, 200L, 100L, 10L, 500L, Instant.now()
        );

        publisher.onMessageDeleted(event);

        verify(eventGateway).publish(
                eq(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.CHAT_MESSAGE_DELETED),
                any(EventEnvelope.class)
        );
    }

    @Test
    public void testOnMessageRead() {
        ChatMessageReadEvent event = new ChatMessageReadEvent(
                UUID.randomUUID(), 1L, 200L, 100L, 10L, Instant.now(), Instant.now()
        );

        publisher.onMessageRead(event);

        verify(eventGateway).publish(
                eq(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.CHAT_MESSAGE_READ),
                any(EventEnvelope.class)
        );
    }

    @Test
    public void testOnTypingStarted() {
        TypingStartedEvent event = new TypingStartedEvent(200L, 1L, 10L, "customer");

        publisher.onTypingStarted(event);

        verify(realtimeGateway).sendToTopic(
                eq("/topic/tenants/1/chat/200/typing"),
                argThat(e -> "TYPING_STARTED".equals(e.getType()) && "START".equals(((TypingPayload) e.getPayload()).getAction()))
        );
    }

    @Test
    public void testOnTypingStopped() {
        TypingStoppedEvent event = new TypingStoppedEvent(200L, 1L, 10L, "customer");

        publisher.onTypingStopped(event);

        verify(realtimeGateway).sendToTopic(
                eq("/topic/tenants/1/chat/200/typing"),
                argThat(e -> "TYPING_STOPPED".equals(e.getType()) && "STOP".equals(((TypingPayload) e.getPayload()).getAction()))
        );
    }
}

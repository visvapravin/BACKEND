package com.forumx.support.chat.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.support.chat.event.durable.ChatEvent;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.mapper.ChatRealtimeMapper;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChatSubscriberTest {

    @Mock private ChatRealtimeMapper realtimeMapper;
    @Mock private RealtimeGateway realtimeGateway;

    private ChatEventSubscriber subscriber;

    @BeforeEach
    public void setUp() {
        subscriber = new ChatEventSubscriber(realtimeMapper, realtimeGateway);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOnChatEventBroadcastsSuccessfully() {
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                UUID.randomUUID(), 1L, 200L, 100L, 10L, "customer", 500L, "Hello", "TEXT", Instant.now()
        );
        EventEnvelope<ChatMessageSentEvent> envelope = EventEnvelope.of(
                "CHAT_MESSAGE_SENT", 1L, "support-chat-service", event
        );

        RealtimeEvent<ChatEvent> mockRealtimeEvent = RealtimeEvent.<ChatEvent>builder()
                .type("CHAT_MESSAGE_SENT")
                .payload(event)
                .build();

        when(realtimeMapper.toRealtimeEvent("CHAT_MESSAGE_SENT", event)).thenReturn(mockRealtimeEvent);

        subscriber.onChatEvent(envelope);

        ArgumentCaptor<RealtimeEvent<ChatEvent>> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway).sendToTopic(eq("/topic/tenants/1/chat/200"), eventCaptor.capture());

        RealtimeEvent<ChatEvent> broadcastEvent = eventCaptor.getValue();
        assertEquals("CHAT_MESSAGE_SENT", broadcastEvent.getType());
        assertEquals(event, broadcastEvent.getPayload());
    }
}

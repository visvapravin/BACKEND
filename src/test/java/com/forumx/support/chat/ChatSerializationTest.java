package com.forumx.support.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ChatSerializationTest {

    private final ObjectMapper objectMapper = MessageSerializer.objectMapper();

    @Test
    public void testChatMessageSentEventSerializationRoundTrip() throws Exception {
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                UUID.randomUUID(), 1L, 200L, 100L, 10L, "customer", 500L, "Hello", "TEXT", Instant.now()
        );
        EventEnvelope<ChatMessageSentEvent> envelope = EventEnvelope.of(
                "CHAT_MESSAGE_SENT", 1L, "support-chat-service", event
        );

        String json = objectMapper.writeValueAsString(envelope);
        assertNotNull(json);

        EventEnvelope<ChatMessageSentEvent> deserialized = objectMapper.readValue(
                json, new TypeReference<EventEnvelope<ChatMessageSentEvent>>() {}
        );

        assertNotNull(deserialized);
        assertEquals(envelope.eventId(), deserialized.eventId());
        assertEquals(envelope.eventType(), deserialized.eventType());
        assertEquals(envelope.tenantId(), deserialized.tenantId());
        assertEquals(envelope.source(), deserialized.source());
        assertEquals(event.content(), deserialized.payload().content());
        assertEquals(event.messageId(), deserialized.payload().messageId());
    }
}

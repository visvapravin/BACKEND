package com.forumx.notification.event;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class NotificationEventSerializationTest {

    private final ObjectMapper mapper = MessageSerializer.objectMapper();

    @Test
    public void testSerializationDeserialization_Success() throws Exception {
        NotificationCreatedEvent payload = new NotificationCreatedEvent(
                123L,
                456L,
                "testuser",
                1L,
                "ANSWER_CREATED",
                "New Answer",
                "User test answered your question",
                789L,
                "QUESTION",
                101112L,
                Instant.parse("2026-07-20T10:00:00Z")
        );

        UUID eventId = UUID.randomUUID();
        EventEnvelope<NotificationCreatedEvent> envelope = new EventEnvelope<>(
                eventId,
                "NOTIFICATION_CREATED",
                1L,
                Instant.parse("2026-07-20T10:05:00Z"),
                "notification-service",
                payload
        );

        String json = mapper.writeValueAsString(envelope);
        assertNotNull(json);
        assertTrue(json.contains("testuser"));
        assertTrue(json.contains("ANSWER_CREATED"));

        EventEnvelope<NotificationCreatedEvent> deserializedEnvelope = mapper.readValue(
                json,
                new TypeReference<EventEnvelope<NotificationCreatedEvent>>() {}
        );

        assertEquals(envelope.eventId(), deserializedEnvelope.eventId());
        assertEquals(envelope.eventType(), deserializedEnvelope.eventType());
        assertEquals(envelope.tenantId(), deserializedEnvelope.tenantId());
        assertEquals(envelope.timestamp(), deserializedEnvelope.timestamp());
        assertEquals(envelope.source(), deserializedEnvelope.source());

        NotificationCreatedEvent deserializedPayload = deserializedEnvelope.payload();
        assertEquals(payload.notificationId(), deserializedPayload.notificationId());
        assertEquals(payload.recipientUserId(), deserializedPayload.recipientUserId());
        assertEquals(payload.recipientUsername(), deserializedPayload.recipientUsername());
        assertEquals(payload.tenantId(), deserializedPayload.tenantId());
        assertEquals(payload.type(), deserializedPayload.type());
        assertEquals(payload.title(), deserializedPayload.title());
        assertEquals(payload.message(), deserializedPayload.message());
        assertEquals(payload.actorId(), deserializedPayload.actorId());
        assertEquals(payload.referenceType(), deserializedPayload.referenceType());
        assertEquals(payload.referenceId(), deserializedPayload.referenceId());
        assertEquals(payload.createdAt(), deserializedPayload.createdAt());
    }
}

package com.forumx.notification.email;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.serializer.MessageSerializer;
import com.forumx.notification.email.event.EmailFailedEvent;
import com.forumx.notification.email.event.EmailSendRequestedEvent;
import com.forumx.notification.email.event.EmailSentEvent;
import com.forumx.notification.email.event.EmailSkippedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class EmailSerializationTest {

    private final ObjectMapper mapper = MessageSerializer.objectMapper();

    @Test
    public void testEmailSendRequestedEventSerialization() throws Exception {
        Map<String, String> model = new HashMap<>();
        model.put("verificationUrl", "http://localhost");

        EmailSendRequestedEvent event = new EmailSendRequestedEvent(
                UUID.randomUUID(),
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.REGISTRATION_VERIFICATION,
                model,
                Instant.now()
        );

        EventEnvelope<EmailSendRequestedEvent> envelope = EventEnvelope.of(
                "EMAIL_SEND_REQUESTED",
                1L,
                "notification-service",
                event
        );

        String json = mapper.writeValueAsString(envelope);
        assertNotNull(json);

        EventEnvelope<EmailSendRequestedEvent> deserialized = mapper.readValue(
                json,
                new TypeReference<EventEnvelope<EmailSendRequestedEvent>>() {}
        );

        assertEquals(envelope.eventId(), deserialized.eventId());
        assertEquals(envelope.tenantId(), deserialized.tenantId());
        assertEquals(envelope.eventType(), deserialized.eventType());
        assertEquals(envelope.payload().recipientEmail(), deserialized.payload().recipientEmail());
        assertEquals(envelope.payload().templateType(), deserialized.payload().templateType());
        assertEquals(envelope.payload().templateModel().get("verificationUrl"), deserialized.payload().templateModel().get("verificationUrl"));
    }

    @Test
    public void testEmailSentEventSerialization() throws Exception {
        EmailSentEvent event = new EmailSentEvent(UUID.randomUUID(), 1L, "test@forumx.com", Instant.now());
        EventEnvelope<EmailSentEvent> envelope = EventEnvelope.of("EMAIL_SENT", 1L, "test", event);

        String json = mapper.writeValueAsString(envelope);
        EventEnvelope<EmailSentEvent> deserialized = mapper.readValue(
                json,
                new TypeReference<EventEnvelope<EmailSentEvent>>() {}
        );

        assertEquals(event.recipientEmail(), deserialized.payload().recipientEmail());
    }

    @Test
    public void testEmailFailedEventSerialization() throws Exception {
        EmailFailedEvent event = new EmailFailedEvent(UUID.randomUUID(), 1L, "test@forumx.com", "Server down", Instant.now());
        EventEnvelope<EmailFailedEvent> envelope = EventEnvelope.of("EMAIL_FAILED", 1L, "test", event);

        String json = mapper.writeValueAsString(envelope);
        EventEnvelope<EmailFailedEvent> deserialized = mapper.readValue(
                json,
                new TypeReference<EventEnvelope<EmailFailedEvent>>() {}
        );

        assertEquals(event.errorMessage(), deserialized.payload().errorMessage());
    }

    @Test
    public void testEmailSkippedEventSerialization() throws Exception {
        EmailSkippedEvent event = new EmailSkippedEvent(UUID.randomUUID(), 1L, "test@forumx.com", Instant.now());
        EventEnvelope<EmailSkippedEvent> envelope = EventEnvelope.of("EMAIL_SKIPPED", 1L, "test", event);

        String json = mapper.writeValueAsString(envelope);
        EventEnvelope<EmailSkippedEvent> deserialized = mapper.readValue(
                json,
                new TypeReference<EventEnvelope<EmailSkippedEvent>>() {}
        );

        assertEquals(event.recipientEmail(), deserialized.payload().recipientEmail());
    }
}

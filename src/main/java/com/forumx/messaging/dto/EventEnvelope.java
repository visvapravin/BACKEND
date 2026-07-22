package com.forumx.messaging.dto;
import java.time.Instant; import java.util.UUID;
public record EventEnvelope<T>(UUID eventId, String eventType, Long tenantId, Instant timestamp, String source, T payload) {
    public static <T> EventEnvelope<T> of(String type, Long tenantId, String source, T payload) { return new EventEnvelope<>(UUID.randomUUID(), type, tenantId, Instant.now(), source, payload); }
}

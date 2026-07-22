package com.forumx.support.chat.event.durable;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageReadEvent(
        UUID eventId,
        Long tenantId,
        Long sessionId,
        Long ticketId,
        Long senderId, // user who read the messages
        Instant upToTimestamp,
        Instant timestamp
) implements ChatEvent {
}

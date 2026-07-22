package com.forumx.support.chat.event.durable;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDeletedEvent(
        UUID eventId,
        Long tenantId,
        Long sessionId,
        Long ticketId,
        Long senderId,
        Long messageId,
        Instant timestamp
) implements ChatEvent {
}

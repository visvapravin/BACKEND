package com.forumx.support.chat.event.durable;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageSentEvent(
        UUID eventId,
        Long tenantId,
        Long sessionId,
        Long ticketId,
        Long senderId,
        String senderUsername,
        Long messageId,
        String content,
        String messageType,
        Instant timestamp
) implements ChatEvent {
}

package com.forumx.support.chat.event.durable;

import java.time.Instant;
import java.util.UUID;

public sealed interface ChatEvent
        permits ChatMessageSentEvent,
                ChatMessageDeletedEvent,
                ChatMessageReadEvent {
    UUID eventId();
    Long tenantId();
    Long sessionId();
    Long ticketId();
    Long senderId();
    Instant timestamp();
}

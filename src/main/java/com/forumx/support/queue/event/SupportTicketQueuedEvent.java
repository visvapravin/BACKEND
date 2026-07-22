package com.forumx.support.queue.event;

import java.time.Instant;

public record SupportTicketQueuedEvent(
        Long ticketId,
        Long tenantId,
        Long creatorId,
        String creatorUsername,
        String subject,
        String priority,
        String status,
        Instant timestamp
) implements SupportQueueEvent {
}

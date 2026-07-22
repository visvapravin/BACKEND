package com.forumx.support.queue.event;

import java.time.Instant;

public record SupportTicketStatusChangedEvent(
        Long ticketId,
        Long tenantId,
        String oldStatus,
        String newStatus,
        Long updatedByUserId,
        Instant timestamp
) implements SupportQueueEvent {
}

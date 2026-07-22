package com.forumx.support.queue.event;

import java.time.Instant;

public record SupportTicketClaimedEvent(
        Long ticketId,
        Long tenantId,
        Long moderatorId,
        String moderatorUsername,
        Instant timestamp
) implements SupportQueueEvent {
}

package com.forumx.support.queue.event;

import java.time.Instant;

public sealed interface SupportQueueEvent
        permits SupportTicketQueuedEvent,
                SupportTicketClaimedEvent,
                SupportTicketStatusChangedEvent {
    Long ticketId();
    Long tenantId();
    Instant timestamp();
}

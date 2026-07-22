package com.forumx.notification.email.event;

import java.time.Instant;
import java.util.UUID;

public record EmailSentEvent(
        UUID eventId,
        Long tenantId,
        String recipientEmail,
        Instant timestamp
) {
}

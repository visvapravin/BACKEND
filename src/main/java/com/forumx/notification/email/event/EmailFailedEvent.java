package com.forumx.notification.email.event;

import java.time.Instant;
import java.util.UUID;

public record EmailFailedEvent(
        UUID eventId,
        Long tenantId,
        String recipientEmail,
        String errorMessage,
        Instant timestamp
) {
}

package com.forumx.notification.email.event;

import com.forumx.notification.email.EmailTemplateType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EmailSendRequestedEvent(
        UUID eventId,
        Long tenantId,
        String recipientEmail,
        String recipientUsername,
        EmailTemplateType templateType,
        Map<String, String> templateModel,
        Instant timestamp
) {
}

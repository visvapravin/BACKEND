package com.forumx.notification.email.dto;

import com.forumx.notification.email.EmailTemplateType;
import java.util.Map;

public record EmailNotificationCommand(
        Long tenantId,
        String recipientEmail,
        String recipientUsername,
        EmailTemplateType templateType,
        Map<String, String> templateModel
) {
}

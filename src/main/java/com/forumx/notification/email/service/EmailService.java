package com.forumx.notification.email.service;

import com.forumx.notification.email.EmailTemplateType;
import java.util.Map;

public interface EmailService {
    void processAndSendEmail(
            Long tenantId,
            String recipientEmail,
            String recipientUsername,
            EmailTemplateType templateType,
            Map<String, String> templateModel
    );
}

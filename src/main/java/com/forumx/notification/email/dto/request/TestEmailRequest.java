package com.forumx.notification.email.dto.request;

import com.forumx.notification.email.EmailTemplateType;
import java.util.Map;
import lombok.Data;

@Data
public class TestEmailRequest {
    private String recipientEmail;
    private String recipientUsername;
    private EmailTemplateType templateType;
    private Map<String, String> templateModel;
}

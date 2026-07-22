package com.forumx.notification.email.template;

import com.forumx.notification.email.EmailTemplateType;
import java.util.Map;

public interface EmailTemplateRenderer {
    String renderHtml(EmailTemplateType type, String username, Map<String, String> model);
    String getSubject(EmailTemplateType type, Map<String, String> model);
}

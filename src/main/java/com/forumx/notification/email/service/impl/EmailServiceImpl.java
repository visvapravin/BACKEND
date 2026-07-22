package com.forumx.notification.email.service.impl;

import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.config.MailProperties;
import com.forumx.notification.email.event.EmailFailedEvent;
import com.forumx.notification.email.event.EmailSentEvent;
import com.forumx.notification.email.event.EmailSkippedEvent;
import com.forumx.notification.email.provider.EmailProvider;
import com.forumx.notification.email.service.EmailService;
import com.forumx.notification.email.template.EmailTemplateRenderer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service("notificationEmailService")
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final MailProperties mailProperties;
    private final EmailProvider emailProvider;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void processAndSendEmail(
            Long tenantId,
            String recipientEmail,
            String recipientUsername,
            EmailTemplateType templateType,
            Map<String, String> templateModel
    ) {
        log.info("Processing email for recipient={}, template={}", recipientEmail, templateType);

        if (!mailProperties.isEnabled()) {
            log.warn("Email delivery skipped because mail.enabled is false. recipient={}", recipientEmail);
            eventPublisher.publishEvent(new EmailSkippedEvent(
                    UUID.randomUUID(),
                    tenantId,
                    recipientEmail,
                    Instant.now()
            ));
            return;
        }

        String subject = emailTemplateRenderer.getSubject(templateType, templateModel);
        String htmlBody = emailTemplateRenderer.renderHtml(templateType, recipientUsername, templateModel);

        try {
            emailProvider.send(recipientEmail, subject, htmlBody);

            eventPublisher.publishEvent(new EmailSentEvent(
                    UUID.randomUUID(),
                    tenantId,
                    recipientEmail,
                    Instant.now()
            ));
        } catch (Exception e) {
            log.error("Email delivery failed for recipient={}, error={}", recipientEmail, e.getMessage(), e);
            eventPublisher.publishEvent(new EmailFailedEvent(
                    UUID.randomUUID(),
                    tenantId,
                    recipientEmail,
                    e.getMessage(),
                    Instant.now()
            ));
            throw e;
        }
    }
}

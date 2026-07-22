package com.forumx.mail;

import com.forumx.notification.email.EmailTemplateType;
import com.forumx.tenant.resolver.TenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final com.forumx.notification.email.service.EmailService notificationEmailService;
    private final TenantResolver tenantResolver;

    @Override
    public void sendVerificationEmail(String toEmail, String username, String verificationUrl) {
        log.info("Sending verification email to {}", toEmail);
        Long tenantId = tenantResolver.resolveTenantId();
        notificationEmailService.processAndSendEmail(
                tenantId,
                toEmail,
                username,
                EmailTemplateType.REGISTRATION_VERIFICATION,
                Map.of("verificationUrl", verificationUrl)
        );
        log.info("Verification email sent to {}", toEmail);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String username, String resetUrl) {
        log.info("Sending password reset email to {}", toEmail);
        Long tenantId = tenantResolver.resolveTenantId();
        notificationEmailService.processAndSendEmail(
                tenantId,
                toEmail,
                username,
                EmailTemplateType.PASSWORD_RESET,
                Map.of("resetUrl", resetUrl)
        );
        log.info("Password reset email sent to {}", toEmail);
    }
}

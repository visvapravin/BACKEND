package com.forumx.notification.email.provider.impl;

import com.forumx.notification.email.config.MailProperties;
import com.forumx.notification.email.provider.EmailProvider;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("Sending email to={}, subject={} using SmtpEmailProvider", to, subject);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom(mailProperties.getFrom());

            mailSender.send(message);
            log.info("Email sent successfully to={}", to);
        } catch (Exception e) {
            log.error("SMTP sending failed for to={}, error={}", to, e.getMessage(), e);
            throw new RuntimeException("SMTP delivery failed", e);
        }
    }
}

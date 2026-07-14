package com.forumx.mail;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateBuilder emailTemplateBuilder;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.port:1025}")
    private int mailPort;

    @Value("${spring.mail.username:noreply@forumx.com}")
    private String mailUsername;

    @PostConstruct
    public void logMailConfiguration() {
        log.info("SMTP Host : {}", mailHost);
        log.info("SMTP Port : {}", mailPort);
        log.info("SMTP Username : {}", mailUsername);
    }

    @Override
    public void sendVerificationEmail(String toEmail, String username, String verificationUrl) {
        try {
            log.info("Preparing to send verification email to {} (username: {})", toEmail, username);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String htmlBody = emailTemplateBuilder.buildVerificationTemplate(verificationUrl, username);

            helper.setTo(toEmail);
            helper.setSubject("Verify your email - ForumX");
            helper.setText(htmlBody, true);
            
            helper.setFrom(mailUsername);

            mailSender.send(message);
            log.info("Verification email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", toEmail, e);
            throw new RuntimeException("Email delivery failed", e);
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String username, String resetUrl) {
        try {
            log.info("Preparing to send password reset email to {} (username: {})", toEmail, username);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String htmlBody = emailTemplateBuilder.buildPasswordResetTemplate(resetUrl, username);

            helper.setTo(toEmail);
            helper.setSubject("Reset your password - ForumX");
            helper.setText(htmlBody, true);

            helper.setFrom(mailUsername);

            mailSender.send(message);
            log.info("Password reset email successfully sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
            throw new RuntimeException("Email delivery failed", e);
        }
    }
}

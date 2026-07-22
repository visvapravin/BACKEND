package com.forumx.notification.email.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.notification.email.config.MailProperties;
import com.forumx.notification.email.provider.impl.SmtpEmailProvider;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

public class EmailProviderTest {

    private JavaMailSender mailSender;
    private MailProperties mailProperties;
    private EmailProvider emailProvider;

    @BeforeEach
    public void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailProperties = mock(MailProperties.class);
        when(mailProperties.getFrom()).thenReturn("noreply@forumx.com");
        emailProvider = new SmtpEmailProvider(mailSender, mailProperties);
    }

    @Test
    public void testSendSuccess() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailProvider.send("to@example.com", "Test Subject", "Test Body");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    public void testSendFailureThrowsException() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP Server unreachable")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class, () -> emailProvider.send("to@example.com", "Test Subject", "Test Body"));
    }
}

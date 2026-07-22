package com.forumx.notification.email.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.config.MailProperties;
import com.forumx.notification.email.event.EmailFailedEvent;
import com.forumx.notification.email.event.EmailSentEvent;
import com.forumx.notification.email.event.EmailSkippedEvent;
import com.forumx.notification.email.provider.EmailProvider;
import com.forumx.notification.email.service.impl.EmailServiceImpl;
import com.forumx.notification.email.template.EmailTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

public class EmailServiceTest {

    private MailProperties mailProperties;
    private EmailProvider emailProvider;
    private EmailTemplateRenderer emailTemplateRenderer;
    private ApplicationEventPublisher eventPublisher;
    private EmailService emailService;

    @BeforeEach
    public void setUp() {
        mailProperties = mock(MailProperties.class);
        emailProvider = mock(EmailProvider.class);
        emailTemplateRenderer = mock(EmailTemplateRenderer.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        emailService = new EmailServiceImpl(mailProperties, emailProvider, emailTemplateRenderer, eventPublisher);
    }

    @Test
    public void testSkippedWhenDisabled() {
        when(mailProperties.isEnabled()).thenReturn(false);

        emailService.processAndSendEmail(
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.GENERIC,
                new HashMap<>()
        );

        verifyNoInteractions(emailProvider);
        verify(emailTemplateRenderer, never()).renderHtml(any(), any(), any());

        ArgumentCaptor<EmailSkippedEvent> captor = ArgumentCaptor.forClass(EmailSkippedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EmailSkippedEvent skippedEvent = captor.getValue();
        assertEquals("test@forumx.com", skippedEvent.recipientEmail());
        assertEquals(1L, skippedEvent.tenantId());
    }

    @Test
    public void testSuccessfulDelivery() {
        when(mailProperties.isEnabled()).thenReturn(true);
        when(emailTemplateRenderer.getSubject(any(), any())).thenReturn("Test Subject");
        when(emailTemplateRenderer.renderHtml(any(), any(), any())).thenReturn("<h1>Hello</h1>");

        Map<String, String> model = Map.of("key", "val");
        emailService.processAndSendEmail(
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.GENERIC,
                model
        );

        verify(emailProvider).send("test@forumx.com", "Test Subject", "<h1>Hello</h1>");

        ArgumentCaptor<EmailSentEvent> captor = ArgumentCaptor.forClass(EmailSentEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EmailSentEvent sentEvent = captor.getValue();
        assertEquals("test@forumx.com", sentEvent.recipientEmail());
        assertEquals(1L, sentEvent.tenantId());
    }

    @Test
    public void testFailedDeliveryThrowsAndPublishesEvent() {
        when(mailProperties.isEnabled()).thenReturn(true);
        when(emailTemplateRenderer.getSubject(any(), any())).thenReturn("Test Subject");
        when(emailTemplateRenderer.renderHtml(any(), any(), any())).thenReturn("<h1>Hello</h1>");
        doThrow(new RuntimeException("SMTP Server Down")).when(emailProvider).send(anyString(), anyString(), anyString());

        assertThrows(RuntimeException.class, () -> emailService.processAndSendEmail(
                1L,
                "test@forumx.com",
                "testuser",
                EmailTemplateType.GENERIC,
                new HashMap<>()
        ));

        ArgumentCaptor<EmailFailedEvent> captor = ArgumentCaptor.forClass(EmailFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EmailFailedEvent failedEvent = captor.getValue();
        assertEquals("test@forumx.com", failedEvent.recipientEmail());
        assertEquals(1L, failedEvent.tenantId());
        assertEquals("SMTP Server Down", failedEvent.errorMessage());
    }
}

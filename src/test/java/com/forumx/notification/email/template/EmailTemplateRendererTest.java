package com.forumx.notification.email.template;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.template.impl.HtmlTemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new HtmlTemplateRenderer();

    @Test
    public void testRegistrationTemplate() {
        Map<String, String> model = Map.of("verificationUrl", "http://verify-link");
        String html = renderer.renderHtml(EmailTemplateType.REGISTRATION_VERIFICATION, "alice", model);
        String subject = renderer.getSubject(EmailTemplateType.REGISTRATION_VERIFICATION, model);

        assertTrue(html.contains("alice"));
        assertTrue(html.contains("http://verify-link"));
        assertEquals("Verify your email - ForumX", subject);
    }

    @Test
    public void testPasswordResetTemplate() {
        Map<String, String> model = Map.of("resetUrl", "http://reset-link");
        String html = renderer.renderHtml(EmailTemplateType.PASSWORD_RESET, "bob", model);
        String subject = renderer.getSubject(EmailTemplateType.PASSWORD_RESET, model);

        assertTrue(html.contains("bob"));
        assertTrue(html.contains("http://reset-link"));
        assertEquals("Reset your password - ForumX", subject);
    }

    @Test
    public void testTicketAssignedTemplate() {
        Map<String, String> model = Map.of("ticketId", "123", "subject", "Hardware issue");
        String html = renderer.renderHtml(EmailTemplateType.TICKET_ASSIGNED, "charlie", model);
        String subject = renderer.getSubject(EmailTemplateType.TICKET_ASSIGNED, model);

        assertTrue(html.contains("charlie"));
        assertTrue(html.contains("Hardware issue"));
        assertTrue(html.contains("123"));
        assertEquals("New Support Ticket Assigned #123", subject);
    }

    @Test
    public void testTicketClosedTemplate() {
        Map<String, String> model = Map.of("ticketId", "123", "subject", "Hardware issue");
        String html = renderer.renderHtml(EmailTemplateType.TICKET_CLOSED, "dave", model);
        String subject = renderer.getSubject(EmailTemplateType.TICKET_CLOSED, model);

        assertTrue(html.contains("dave"));
        assertTrue(html.contains("Hardware issue"));
        assertTrue(html.contains("123"));
        assertEquals("Support Ticket Resolved #123", subject);
    }

    @Test
    public void testChatMessageTemplate() {
        Map<String, String> model = Map.of("ticketId", "999", "senderName", "Agent 007", "messagePreview", "Can you hear me?");
        String html = renderer.renderHtml(EmailTemplateType.CHAT_MESSAGE, "eve", model);
        String subject = renderer.getSubject(EmailTemplateType.CHAT_MESSAGE, model);

        assertTrue(html.contains("eve"));
        assertTrue(html.contains("Agent 007"));
        assertTrue(html.contains("Can you hear me?"));
        assertTrue(html.contains("999"));
        assertEquals("New message on Ticket #999", subject);
    }

    @Test
    public void testGenericTemplate() {
        Map<String, String> model = Map.of("subject", "Notice", "message", "System will reboot in 5 minutes");
        String html = renderer.renderHtml(EmailTemplateType.GENERIC, "admin", model);
        String subject = renderer.getSubject(EmailTemplateType.GENERIC, model);

        assertTrue(html.contains("admin"));
        assertTrue(html.contains("System will reboot in 5 minutes"));
        assertEquals("Notice - ForumX", subject);
    }
}

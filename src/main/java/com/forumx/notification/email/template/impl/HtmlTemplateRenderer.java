package com.forumx.notification.email.template.impl;

import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.template.EmailTemplateRenderer;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HtmlTemplateRenderer implements EmailTemplateRenderer {

    private String buildCommonLayout(String title, String bodyContent) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>%s</title>
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f5f7; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 40px auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
                        .header { text-align: center; border-bottom: 1px solid #eef2f6; padding-bottom: 24px; }
                        .header h1 { color: #1e293b; margin: 0; font-size: 26px; font-weight: 700; }
                        .content { padding: 32px 0; line-height: 1.6; color: #475569; font-size: 16px; }
                        .btn-container { text-align: center; margin: 36px 0; }
                        .btn { display: inline-block; padding: 14px 28px; background-color: #2563eb; color: #ffffff !important; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px; transition: background-color 0.2s; }
                        .link-raw { word-break: break-all; color: #2563eb; text-decoration: underline; }
                        .footer { border-top: 1px solid #eef2f6; padding-top: 24px; text-align: center; font-size: 13px; color: #94a3b8; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>ForumX</h1>
                        </div>
                        <div class="content">
                            %s
                        </div>
                        <div class="footer">
                            <p>This is an automated message. Please do not reply directly to this email.</p>
                            <p>&copy; 2026 ForumX. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(title, bodyContent);
    }

    @Override
    public String renderHtml(EmailTemplateType type, String username, Map<String, String> model) {
        String name = username != null ? username : "User";
        String body;

        switch (type) {
            case REGISTRATION_VERIFICATION:
                String verificationUrl = model.getOrDefault("verificationUrl", "#");
                body = """
                        <p>Hi <strong>%s</strong>,</p>
                        <p>Thank you for signing up for ForumX! Please confirm your email address to activate your account:</p>
                        <div class="btn-container">
                            <a href="%s" class="btn">Verify Email Address</a>
                        </div>
                        <p>If the button doesn't work, copy and paste this link into your browser:</p>
                        <p><a href="%s" class="link-raw">%s</a></p>
                        <p>This verification link will expire in 24 hours.</p>
                        """.formatted(name, verificationUrl, verificationUrl, verificationUrl);
                return buildCommonLayout("Verify your Email - ForumX", body);

            case PASSWORD_RESET:
                String resetUrl = model.getOrDefault("resetUrl", "#");
                body = """
                        <p>Hi <strong>%s</strong>,</p>
                        <p>We received a request to reset your password. Click the button below to choose a new password:</p>
                        <div class="btn-container">
                            <a href="%s" class="btn" style="background-color: #dc2626;">Reset Password</a>
                        </div>
                        <p>If the button doesn't work, copy and paste this link into your browser:</p>
                        <p><a href="%s" class="link-raw">%s</a></p>
                        <p>This link will expire in 30 minutes.</p>
                        """.formatted(name, resetUrl, resetUrl, resetUrl);
                return buildCommonLayout("Reset your Password - ForumX", body);

            case TICKET_ASSIGNED:
                String assignedTicketId = model.getOrDefault("ticketId", "N/A");
                String assignedSubject = model.getOrDefault("subject", "");
                body = """
                        <p>Hi <strong>%s</strong>,</p>
                        <p>A new support ticket has been assigned to you:</p>
                        <ul>
                            <li><strong>Ticket ID:</strong> %s</li>
                            <li><strong>Subject:</strong> %s</li>
                        </ul>
                        <p>Please log into the moderator portal to view and handle this ticket.</p>
                        """.formatted(name, assignedTicketId, assignedSubject);
                return buildCommonLayout("Ticket Assigned - ForumX", body);

            case TICKET_CLOSED:
                String closedTicketId = model.getOrDefault("ticketId", "N/A");
                String closedSubject = model.getOrDefault("subject", "");
                body = """
                        <p>Hi <strong>%s</strong>,</p>
                        <p>Your support ticket has been resolved and closed:</p>
                        <ul>
                            <li><strong>Ticket ID:</strong> %s</li>
                            <li><strong>Subject:</strong> %s</li>
                        </ul>
                        <p>If you have any further questions, feel free to open a new support ticket.</p>
                        """.formatted(name, closedTicketId, closedSubject);
                return buildCommonLayout("Ticket Resolved - ForumX", body);

            case CHAT_MESSAGE:
                String chatTicketId = model.getOrDefault("ticketId", "N/A");
                String senderName = model.getOrDefault("senderName", "Someone");
                String preview = model.getOrDefault("messagePreview", "");
                body = """
                        <p>Hi <strong>%s</strong>,</p>
                        <p>You received a new message regarding Support Ticket #%s:</p>
                        <blockquote style="border-left: 4px solid #2563eb; padding-left: 16px; margin: 20px 0; color: #475569; font-style: italic;">
                            %s
                        </blockquote>
                        <p><strong>From:</strong> %s</p>
                        <p>Please log in to continue the live chat conversation.</p>
                        """.formatted(name, chatTicketId, preview, senderName);
                return buildCommonLayout("New Support Chat Message - ForumX", body);

            case GENERIC:
            default:
                String genericSubject = model.getOrDefault("subject", "Notification");
                String genericMsg = model.getOrDefault("message", "");
                body = """
                        <p>Hi <strong>%s</strong>,</p>
                        <p>%s</p>
                        """.formatted(name, genericMsg);
                return buildCommonLayout(genericSubject + " - ForumX", body);
        }
    }

    @Override
    public String getSubject(EmailTemplateType type, Map<String, String> model) {
        return switch (type) {
            case REGISTRATION_VERIFICATION -> "Verify your email - ForumX";
            case PASSWORD_RESET -> "Reset your password - ForumX";
            case TICKET_ASSIGNED -> "New Support Ticket Assigned #" + model.getOrDefault("ticketId", "");
            case TICKET_CLOSED -> "Support Ticket Resolved #" + model.getOrDefault("ticketId", "");
            case CHAT_MESSAGE -> "New message on Ticket #" + model.getOrDefault("ticketId", "");
            case GENERIC -> model.containsKey("subject") ? model.get("subject") + " - ForumX" : "Notification - ForumX";
        };
    }
}

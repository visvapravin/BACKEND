package com.forumx.mail;

import org.springframework.stereotype.Component;

@Component
public class EmailTemplateBuilder {

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

    public String buildVerificationTemplate(String verificationUrl, String username) {
        String body = """
                <p>Hi <strong>%s</strong>,</p>
                <p>Thank you for signing up for ForumX! Please confirm your email address to activate your account and start participating in the discussions:</p>
                <div class="btn-container">
                    <a href="%s" class="btn">Verify Email Address</a>
                </div>
                <p>If the button doesn't work, you can copy and paste the following link into your browser:</p>
                <p><a href="%s" class="link-raw">%s</a></p>
                <p>This verification link will expire in 24 hours.</p>
                <p>If you did not request this, you can safely ignore this email.</p>
                """.formatted(username, verificationUrl, verificationUrl, verificationUrl);
        return buildCommonLayout("Verify your Email - ForumX", body);
    }

    public String buildPasswordResetTemplate(String resetUrl, String username) {
        String body = """
                <p>Hi <strong>%s</strong>,</p>
                <p>We received a request to reset your password. Click the button below to choose a new password:</p>
                <div class="btn-container">
                    <a href="%s" class="btn" style="background-color: #dc2626;">Reset Password</a>
                </div>
                <p>If the button doesn't work, copy and paste this link into your browser:</p>
                <p><a href="%s" class="link-raw">%s</a></p>
                <p>This link will expire in 30 minutes.</p>
                <p>If you did not request a password reset, please secure your account.</p>
                """.formatted(username, resetUrl, resetUrl, resetUrl);
        return buildCommonLayout("Reset your Password - ForumX", body);
    }
}

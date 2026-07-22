package com.forumx.notification.email.provider;

public interface EmailProvider {
    void send(String to, String subject, String htmlBody);
}

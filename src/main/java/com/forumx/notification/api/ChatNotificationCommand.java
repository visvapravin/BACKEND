package com.forumx.notification.api;

public record ChatNotificationCommand(
        Long tenantId,
        Long recipientId,
        Long actorId,
        String actorUsername,
        Long sessionId,
        Long ticketId,
        String messagePreview
) {
}

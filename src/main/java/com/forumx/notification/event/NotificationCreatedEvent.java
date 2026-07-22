package com.forumx.notification.event;

import java.time.Instant;

public record NotificationCreatedEvent(
    Long notificationId,
    Long recipientUserId,
    String recipientUsername,
    Long tenantId,
    String type,
    String title,
    String message,
    Long actorId,
    String referenceType,
    Long referenceId,
    Instant createdAt
) {
}

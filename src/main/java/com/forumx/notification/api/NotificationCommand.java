package com.forumx.notification.api;

import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;

/** Stable command contract used by future modules to create notifications. */
public record NotificationCommand(
        Long tenantId,
        Long recipientId,
        Long actorId,
        NotificationType notificationType,
        String title,
        String message,
        ReferenceType referenceType,
        Long referenceId
) {
}

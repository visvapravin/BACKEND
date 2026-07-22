package com.forumx.notification.dto.response;

import java.time.Instant;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;

public record NotificationSummary(
        Long id,
        Long recipientId,
        Long actorId,
        NotificationType notificationType,
        String title,
        String message,
        ReferenceType referenceType,
        Long referenceId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {}

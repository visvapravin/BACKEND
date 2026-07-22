package com.forumx.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationEvent(
        UUID notificationId,
        Long tenantId,
        Long userId,
        String email,
        String subject,
        String body,
        String type,
        Instant createdAt
) {}

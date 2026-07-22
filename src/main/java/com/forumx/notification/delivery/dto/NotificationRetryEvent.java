package com.forumx.notification.delivery.dto;

import java.time.Instant;

public record NotificationRetryEvent(
        String correlationId,
        Long tenantId,
        Long notificationId,
        String channel,
        int attemptNumber,
        Instant scheduledAt
) {}

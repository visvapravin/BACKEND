package com.forumx.notification.delivery.dto;

import java.time.Instant;

public record NotificationDeadLetterEvent(
        String correlationId,
        Long tenantId,
        Long notificationId,
        String channel,
        int totalAttempts,
        String failureReason,
        Instant failedAt
) {}

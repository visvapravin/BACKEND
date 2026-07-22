package com.forumx.notification.delivery.dto.message;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import java.time.Instant;

public record NotificationRetryPayload(
        int version,
        Long notificationId,
        DeliveryChannel channel,
        int attemptNumber,
        String correlationId,
        Instant timestamp
) {
    public NotificationRetryPayload(Long notificationId, DeliveryChannel channel, int attemptNumber, String correlationId) {
        this(1, notificationId, channel, attemptNumber, correlationId, Instant.now());
    }
}

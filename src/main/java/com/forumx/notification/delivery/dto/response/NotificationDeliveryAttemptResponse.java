package com.forumx.notification.delivery.dto.response;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.entity.DeliveryProvider;
import com.forumx.notification.delivery.entity.DeliveryStatus;
import java.time.Instant;

public record NotificationDeliveryAttemptResponse(
        Long id,
        Long tenantId,
        Long notificationId,
        DeliveryChannel channel,
        DeliveryProvider provider,
        DeliveryStatus status,
        int attemptNumber,
        String correlationId,
        String providerResponse,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        Instant nextRetryAt,
        Instant createdAt
) {
}

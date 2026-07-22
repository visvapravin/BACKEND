package com.forumx.notification.delivery.dto.message;

import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.DeliveryChannel;
import java.time.Instant;

public record NotificationDeadLetterPayload(
        int version,
        Long notificationId,
        int attemptNumber,
        DeliveryChannel channel,
        String reason,
        String correlationId,
        NotificationDeliveryContext deliveryContext,
        Instant timestamp
) {
    public NotificationDeadLetterPayload(Long notificationId, int attemptNumber, DeliveryChannel channel, String reason, String correlationId, NotificationDeliveryContext deliveryContext) {
        this(1, notificationId, attemptNumber, channel, reason, correlationId, deliveryContext, Instant.now());
    }
}

package com.forumx.notification.delivery.domain;

import com.forumx.auth.entity.User;
import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.entity.DeliveryProvider;
import com.forumx.notification.delivery.entity.NotificationDeliveryAttempt;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.entity.NotificationType;
import com.forumx.tenant.entity.Tenant;
import java.util.Map;

public record NotificationDeliveryContext(
        Notification notification,
        User recipient,
        Tenant tenant,
        NotificationType notificationType,
        DeliveryChannel channel,
        DeliveryProvider provider,
        int priority,
        String decision,
        String correlationId,
        NotificationEventPayload originalEventPayload,
        NotificationDeliveryAttempt currentAttempt,
        Map<String, Object> metadata
) {
    public NotificationDeliveryContext withAttempt(NotificationDeliveryAttempt attempt) {
        return new NotificationDeliveryContext(
                notification,
                recipient,
                tenant,
                notificationType,
                channel,
                provider,
                priority,
                decision,
                correlationId,
                originalEventPayload,
                attempt,
                metadata
        );
    }
}

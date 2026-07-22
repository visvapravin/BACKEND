package com.forumx.notification.delivery.executor;

import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import java.time.Duration;

public interface RetryCoordinator {
    void scheduleRetry(NotificationDeliveryContext context, Duration delay);
    void sendToDeadLetter(NotificationDeliveryContext context, String reason);
}

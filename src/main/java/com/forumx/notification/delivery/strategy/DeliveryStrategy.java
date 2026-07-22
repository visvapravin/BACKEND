package com.forumx.notification.delivery.strategy;

import com.forumx.notification.delivery.domain.DeliveryExecutionResult;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.DeliveryChannel;

public interface DeliveryStrategy {
    DeliveryChannel getChannel();
    DeliveryExecutionResult deliver(NotificationDeliveryContext context);
}

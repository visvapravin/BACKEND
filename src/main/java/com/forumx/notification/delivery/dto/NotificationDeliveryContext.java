package com.forumx.notification.delivery.dto;

import java.util.Map;
import com.forumx.notification.delivery.domain.DeliveryProvider;
import com.forumx.notification.dispatcher.NotificationDeliveryRequest;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.preference.domain.DeliveryChannel;
import com.forumx.notification.preference.domain.NotificationPriority;
import com.forumx.notification.preference.dto.NotificationDeliveryDecision;

public record NotificationDeliveryContext(
        String correlationId,
        Notification notification,
        NotificationDeliveryRequest originalRequest,
        DeliveryChannel channel,
        DeliveryProvider provider,
        int attemptNumber,
        NotificationPriority priority,
        NotificationDeliveryDecision decision,
        Map<String, Object> metadata
) {}

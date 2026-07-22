package com.forumx.notification.dispatcher;

import com.forumx.notification.dto.NotificationEvent;
import com.forumx.notification.entity.Notification;

public record NotificationDeliveryRequest(
        Notification notification,
        NotificationEvent event
) {}

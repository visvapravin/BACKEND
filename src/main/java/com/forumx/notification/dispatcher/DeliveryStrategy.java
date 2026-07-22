package com.forumx.notification.dispatcher;

public interface DeliveryStrategy {
    void deliver(NotificationDeliveryRequest request);
}

package com.forumx.notification.delivery.exception;

import com.forumx.notification.delivery.entity.DeliveryChannel;

public class UnsupportedDeliveryChannelException extends DeliveryException {
    public UnsupportedDeliveryChannelException(DeliveryChannel channel) {
        super("Unsupported delivery channel: " + channel);
    }
}

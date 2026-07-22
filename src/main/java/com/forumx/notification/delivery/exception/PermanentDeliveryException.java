package com.forumx.notification.delivery.exception;

public class PermanentDeliveryException extends DeliveryException {
    public PermanentDeliveryException(String message) {
        super(message);
    }

    public PermanentDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

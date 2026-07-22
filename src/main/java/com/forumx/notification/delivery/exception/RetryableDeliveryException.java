package com.forumx.notification.delivery.exception;

public class RetryableDeliveryException extends DeliveryException {
    public RetryableDeliveryException(String message) {
        super(message);
    }

    public RetryableDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

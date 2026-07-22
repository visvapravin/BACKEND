package com.forumx.notification.delivery.domain;

public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    RETRYING,
    DEAD_LETTER
}

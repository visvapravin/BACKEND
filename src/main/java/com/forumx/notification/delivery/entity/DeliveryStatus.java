package com.forumx.notification.delivery.entity;

public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    RETRYING,
    DEAD_LETTER
}

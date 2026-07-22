package com.forumx.notification.delivery.domain;

import java.time.Duration;

public record DeliveryExecutionResult(
        boolean success,
        String providerResponse,
        String errorMessage,
        Duration duration
) {
    public static DeliveryExecutionResult success(String providerResponse, Duration duration) {
        return new DeliveryExecutionResult(true, providerResponse, null, duration);
    }

    public static DeliveryExecutionResult failure(String errorMessage, Duration duration) {
        return new DeliveryExecutionResult(false, null, errorMessage, duration);
    }
}

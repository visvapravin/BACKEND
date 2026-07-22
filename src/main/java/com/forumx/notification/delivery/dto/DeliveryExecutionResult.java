package com.forumx.notification.delivery.dto;

import com.forumx.notification.delivery.domain.DeliveryProvider;
import com.forumx.notification.preference.domain.DeliveryChannel;

public record DeliveryExecutionResult(
        DeliveryChannel channel,
        DeliveryProvider provider,
        boolean success,
        String providerResponse,
        String errorMessage,
        long durationMs,
        boolean retryable
) {
    public static DeliveryExecutionResult success(DeliveryChannel channel, DeliveryProvider provider, String response, long durationMs) {
        return new DeliveryExecutionResult(channel, provider, true, response, null, durationMs, false);
    }

    public static DeliveryExecutionResult failure(DeliveryChannel channel, DeliveryProvider provider, String error, long durationMs, boolean retryable) {
        return new DeliveryExecutionResult(channel, provider, false, null, error, durationMs, retryable);
    }
}

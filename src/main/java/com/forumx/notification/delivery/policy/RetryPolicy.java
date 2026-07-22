package com.forumx.notification.delivery.policy;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import java.time.Duration;

public interface RetryPolicy {
    default DeliveryChannel getChannel() {
        return null;
    }
    boolean shouldRetry(int attemptNumber, Throwable throwable);
    Duration getNextRetryDelay(int attemptNumber);
    int getMaxAttempts();
}

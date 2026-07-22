package com.forumx.notification.delivery.policy;

import com.forumx.notification.delivery.config.DeliveryRetryProperties;
import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.exception.PermanentDeliveryException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private final DeliveryRetryProperties retryProperties;

    @Override
    public DeliveryChannel getChannel() {
        return null;
    }

    @Override
    public Duration getNextRetryDelay(int attemptNumber) {
        return switch (attemptNumber) {
            case 1 -> Duration.ofSeconds(retryProperties.getAttempt1DelaySeconds());
            case 2 -> Duration.ofSeconds(retryProperties.getAttempt2DelaySeconds());
            case 3 -> Duration.ofSeconds(retryProperties.getAttempt3DelaySeconds());
            default -> Duration.ZERO;
        };
    }

    @Override
    public boolean shouldRetry(int attemptNumber, Throwable exception) {
        if (exception instanceof PermanentDeliveryException) {
            return false;
        }
        return attemptNumber < getMaxAttempts();
    }

    @Override
    public int getMaxAttempts() {
        return retryProperties.getMaxAttempts();
    }
}

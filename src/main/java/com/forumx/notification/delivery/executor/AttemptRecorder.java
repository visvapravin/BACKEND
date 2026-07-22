package com.forumx.notification.delivery.executor;

import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.DeliveryStatus;
import com.forumx.notification.delivery.entity.NotificationDeliveryAttempt;
import com.forumx.notification.delivery.repository.NotificationDeliveryAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AttemptRecorder {

    private final NotificationDeliveryAttemptRepository attemptRepository;

    @Transactional
    public NotificationDeliveryAttempt recordStart(NotificationDeliveryContext context, Instant startedAt) {
        int attemptNum = context.currentAttempt() != null ? context.currentAttempt().getAttemptNumber() + 1 : 1;
        NotificationDeliveryAttempt attempt = NotificationDeliveryAttempt.builder()
                .tenant(context.tenant())
                .notification(context.notification())
                .channel(context.channel())
                .provider(context.provider())
                .status(DeliveryStatus.PROCESSING)
                .attemptNumber(attemptNum)
                .correlationId(context.correlationId())
                .startedAt(startedAt)
                .build();
        return attemptRepository.save(attempt);
    }

    @Transactional
    public void recordSuccess(NotificationDeliveryAttempt attempt, String providerResponse, Instant completedAt, Duration duration) {
        attempt.setStatus(DeliveryStatus.DELIVERED);
        attempt.setProviderResponse(providerResponse);
        attempt.setCompletedAt(completedAt);
        attempt.setDurationMs(duration.toMillis());
        attemptRepository.save(attempt);
    }

    @Transactional
    public void recordRetry(NotificationDeliveryAttempt attempt, String errorMessage, Instant nextRetryAt, Instant completedAt, Duration duration) {
        attempt.setStatus(DeliveryStatus.RETRYING);
        attempt.setErrorMessage(errorMessage);
        attempt.setNextRetryAt(nextRetryAt);
        attempt.setCompletedAt(completedAt);
        attempt.setDurationMs(duration.toMillis());
        attemptRepository.save(attempt);
    }

    @Transactional
    public void recordDeadLetter(NotificationDeliveryAttempt attempt, String errorMessage, Instant completedAt, Duration duration) {
        attempt.setStatus(DeliveryStatus.DEAD_LETTER);
        attempt.setErrorMessage(errorMessage);
        attempt.setCompletedAt(completedAt);
        attempt.setDurationMs(duration.toMillis());
        attemptRepository.save(attempt);
    }
}

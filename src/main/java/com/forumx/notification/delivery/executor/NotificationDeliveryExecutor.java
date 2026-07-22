package com.forumx.notification.delivery.executor;

import com.forumx.notification.delivery.domain.DeliveryExecutionResult;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.NotificationDeliveryAttempt;
import com.forumx.notification.delivery.exception.PermanentDeliveryException;
import com.forumx.notification.delivery.metrics.NotificationDeliveryMetricsService;
import com.forumx.notification.delivery.policy.RetryPolicy;
import com.forumx.notification.delivery.policy.RetryPolicyResolver;
import com.forumx.notification.delivery.strategy.DeliveryStrategy;
import com.forumx.notification.delivery.strategy.DeliveryStrategyRegistry;
import com.forumx.notification.delivery.util.CorrelationIdGenerator;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryExecutor {

    private final DeliveryStrategyRegistry strategyRegistry;
    private final RetryPolicyResolver retryPolicyResolver;
    private final AttemptRecorder attemptRecorder;
    private final RetryCoordinator retryCoordinator;
    private final NotificationDeliveryMetricsService metricsService;
    private final CorrelationIdGenerator correlationIdGenerator;

    @Transactional
    public NotificationDeliveryAttempt execute(NotificationDeliveryContext inputContext) {
        NotificationDeliveryContext context = ensureCorrelationId(inputContext);
        DeliveryStrategy strategy = strategyRegistry.getStrategy(context.channel());
        RetryPolicy retryPolicy = retryPolicyResolver.resolvePolicy(context.channel());

        Instant startedAt = Instant.now();
        NotificationDeliveryAttempt attempt = attemptRecorder.recordStart(context, startedAt);
        NotificationDeliveryContext enrichedContext = context.withAttempt(attempt);

        try {
            DeliveryExecutionResult result = strategy.deliver(enrichedContext);
            Instant completedAt = Instant.now();

            if (result.success()) {
                handleSuccess(enrichedContext, attempt, result.providerResponse(), completedAt, result.duration());
            } else {
                handleFailure(enrichedContext, attempt, new PermanentDeliveryException(result.errorMessage()), completedAt, result.duration(), retryPolicy);
            }
        } catch (Exception e) {
            Instant completedAt = Instant.now();
            Duration duration = Duration.between(startedAt, completedAt);
            handleFailure(enrichedContext, attempt, e, completedAt, duration, retryPolicy);
        }

        return attempt;
    }

    private NotificationDeliveryContext ensureCorrelationId(NotificationDeliveryContext context) {
        if (context.correlationId() == null || context.correlationId().isBlank()) {
            String generatedId = correlationIdGenerator.generateCorrelationId();
            return new NotificationDeliveryContext(
                    context.notification(),
                    context.recipient(),
                    context.tenant(),
                    context.notificationType(),
                    context.channel(),
                    context.provider(),
                    context.priority(),
                    context.decision(),
                    generatedId,
                    context.originalEventPayload(),
                    context.currentAttempt(),
                    context.metadata()
            );
        }
        return context;
    }

    private void handleSuccess(NotificationDeliveryContext context, NotificationDeliveryAttempt attempt, String providerResponse, Instant completedAt, Duration duration) {
        attemptRecorder.recordSuccess(attempt, providerResponse, completedAt, duration);
        String tenantSlug = context.tenant() != null ? context.tenant().getSlug() : null;
        metricsService.recordSuccess(context.channel(), context.provider(), tenantSlug, duration);
    }

    private void handleFailure(NotificationDeliveryContext context, NotificationDeliveryAttempt attempt, Exception exception, Instant completedAt, Duration duration, RetryPolicy retryPolicy) {
        String tenantSlug = context.tenant() != null ? context.tenant().getSlug() : null;
        metricsService.recordFailure(context.channel(), context.provider(), tenantSlug);

        boolean shouldRetry = retryPolicy.shouldRetry(attempt.getAttemptNumber(), exception);
        if (shouldRetry) {
            Duration delay = retryPolicy.getNextRetryDelay(attempt.getAttemptNumber());
            attemptRecorder.recordRetry(attempt, exception.getMessage(), completedAt.plus(delay), completedAt, duration);
            metricsService.recordRetry(context.channel(), context.provider(), tenantSlug);
            retryCoordinator.scheduleRetry(context, delay);
        } else {
            attemptRecorder.recordDeadLetter(attempt, exception.getMessage(), completedAt, duration);
            metricsService.recordDeadLetter(context.channel(), context.provider(), tenantSlug);
            retryCoordinator.sendToDeadLetter(context, exception.getMessage());
        }
    }
}

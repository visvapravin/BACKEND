package com.forumx.notification.delivery.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.forumx.notification.preference.domain.DeliveryChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeliveryMetricsService {

    private final AtomicLong totalAttempts = new AtomicLong();
    private final AtomicLong successfulDeliveries = new AtomicLong();
    private final AtomicLong failedDeliveries = new AtomicLong();
    private final AtomicLong retriesCount = new AtomicLong();
    private final AtomicLong deadLettersCount = new AtomicLong();

    private final ConcurrentHashMap<DeliveryChannel, AtomicLong> channelSuccesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DeliveryChannel, AtomicLong> channelFailures = new ConcurrentHashMap<>();

    public void recordAttempt(DeliveryChannel channel) {
        totalAttempts.incrementAndGet();
    }

    public void recordSuccess(DeliveryChannel channel, long durationMs) {
        successfulDeliveries.incrementAndGet();
        channelSuccesses.computeIfAbsent(channel, c -> new AtomicLong()).incrementAndGet();
        log.debug("Metric recorded: SUCCESS for channel={}, duration={}ms", channel, durationMs);
    }

    public void recordFailure(DeliveryChannel channel, boolean retryable) {
        failedDeliveries.incrementAndGet();
        channelFailures.computeIfAbsent(channel, c -> new AtomicLong()).incrementAndGet();
        if (retryable) {
            retriesCount.incrementAndGet();
        }
        log.debug("Metric recorded: FAILURE for channel={}, retryable={}", channel, retryable);
    }

    public void recordDeadLetter(DeliveryChannel channel) {
        deadLettersCount.incrementAndGet();
        log.warn("Metric recorded: DEAD_LETTER for channel={}", channel);
    }

    public MetricsSummary getMetricsSummary() {
        return new MetricsSummary(
                totalAttempts.get(),
                successfulDeliveries.get(),
                failedDeliveries.get(),
                retriesCount.get(),
                deadLettersCount.get()
        );
    }

    public record MetricsSummary(
            long totalAttempts,
            long successfulDeliveries,
            long failedDeliveries,
            long totalRetries,
            long deadLetters
    ) {}
}

package com.forumx.notification.delivery.metrics;

import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.entity.DeliveryProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordSuccess(DeliveryChannel channel, DeliveryProvider provider, String tenantSlug, Duration duration) {
        meterRegistry.counter("forumx.delivery.success",
                "channel", channel != null ? channel.name() : "UNKNOWN",
                "provider", provider != null ? provider.name() : "NONE",
                "tenant", tenantSlug != null ? tenantSlug : "UNKNOWN").increment();

        Timer.builder("forumx.delivery.duration")
                .tag("channel", channel != null ? channel.name() : "UNKNOWN")
                .tag("provider", provider != null ? provider.name() : "NONE")
                .tag("tenant", tenantSlug != null ? tenantSlug : "UNKNOWN")
                .register(meterRegistry)
                .record(duration);
    }

    public void recordFailure(DeliveryChannel channel, DeliveryProvider provider, String tenantSlug) {
        meterRegistry.counter("forumx.delivery.failure",
                "channel", channel != null ? channel.name() : "UNKNOWN",
                "provider", provider != null ? provider.name() : "NONE",
                "tenant", tenantSlug != null ? tenantSlug : "UNKNOWN").increment();
    }

    public void recordRetry(DeliveryChannel channel, DeliveryProvider provider, String tenantSlug) {
        meterRegistry.counter("forumx.delivery.retry",
                "channel", channel != null ? channel.name() : "UNKNOWN",
                "provider", provider != null ? provider.name() : "NONE",
                "tenant", tenantSlug != null ? tenantSlug : "UNKNOWN").increment();
    }

    public void recordDeadLetter(DeliveryChannel channel, DeliveryProvider provider, String tenantSlug) {
        meterRegistry.counter("forumx.delivery.deadletter",
                "channel", channel != null ? channel.name() : "UNKNOWN",
                "provider", provider != null ? provider.name() : "NONE",
                "tenant", tenantSlug != null ? tenantSlug : "UNKNOWN").increment();
    }
}

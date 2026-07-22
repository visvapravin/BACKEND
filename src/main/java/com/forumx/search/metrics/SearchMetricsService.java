package com.forumx.search.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordSearchRequest(String searchType, String provider) {
        meterRegistry.counter("forumx.search.requests",
                "type", searchType != null ? searchType : "GLOBAL",
                "provider", provider != null ? provider : "POSTGRES").increment();
    }

    public void recordSearchDuration(String searchType, Duration duration) {
        Timer.builder("forumx.search.duration")
                .tag("type", searchType != null ? searchType : "GLOBAL")
                .register(meterRegistry)
                .record(duration);
    }

    public void recordEmptyResult(String searchType) {
        meterRegistry.counter("forumx.search.empty",
                "type", searchType != null ? searchType : "GLOBAL").increment();
    }
}

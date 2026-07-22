package com.forumx.notification.delivery.util.impl;

import com.forumx.notification.delivery.util.CorrelationIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidCorrelationIdGenerator implements CorrelationIdGenerator {

    @Override
    public String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}

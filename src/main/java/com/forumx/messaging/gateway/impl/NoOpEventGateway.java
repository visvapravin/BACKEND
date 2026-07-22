package com.forumx.messaging.gateway.impl;

import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation of {@link EventGateway} used when messaging (RabbitMQ) is disabled.
 *
 * <p>This implementation guarantees that the Spring Application Context can load cleanly
 * and components depending on {@code EventGateway} (such as {@code RabbitMqRetryCoordinator})
 * degrade gracefully without causing startup errors.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEventGateway implements EventGateway {

    @Override
    public void publish(String exchange, String routingKey, EventEnvelope<?> event) {
        publish(exchange, routingKey, event, null);
    }

    @Override
    public void publish(String exchange, String routingKey, EventEnvelope<?> event, CorrelationData correlationData) {
        log.debug("[NoOpEventGateway] Messaging is disabled. Skipping publish to exchange='{}', routingKey='{}', eventId={}",
                exchange, routingKey, event != null ? event.eventId() : null);
    }
}

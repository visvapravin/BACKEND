package com.forumx.messaging.gateway.impl;
import com.forumx.messaging.dto.EventEnvelope; import com.forumx.messaging.exception.MessagingOperationException; import com.forumx.messaging.gateway.EventGateway; import lombok.RequiredArgsConstructor; import org.springframework.amqp.rabbit.connection.CorrelationData; import org.springframework.amqp.rabbit.core.RabbitTemplate; import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="forumx.messaging.enabled",havingValue="true")
public class RabbitEventGateway implements EventGateway {
    private final RabbitTemplate rabbitTemplate;
    @Override public void publish(String exchange, String routingKey, EventEnvelope<?> event) { publish(exchange, routingKey, event, new CorrelationData(event.eventId().toString())); }
    @Override public void publish(String exchange, String routingKey, EventEnvelope<?> event, CorrelationData data) { try { rabbitTemplate.convertAndSend(exchange, routingKey, event, data); } catch (RuntimeException e) { throw new MessagingOperationException("Failed to publish event " + event.eventType(), e); } }
}

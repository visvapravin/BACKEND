package com.forumx.messaging.publisher;
import com.forumx.messaging.dto.EventEnvelope; import com.forumx.messaging.gateway.EventGateway; import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.amqp.rabbit.connection.CorrelationData; import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(name="forumx.messaging.enabled",havingValue="true")
public class EventPublisher {
    private final EventGateway eventGateway;
    public void publish(String exchange, String routingKey, EventEnvelope<?> event) { CorrelationData data=new CorrelationData(event.eventId().toString()); eventGateway.publish(exchange,routingKey,event,data); log.debug("Published eventId={}, type={}", event.eventId(), event.eventType()); }
}

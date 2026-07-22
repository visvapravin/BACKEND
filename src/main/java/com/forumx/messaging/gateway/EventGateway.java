package com.forumx.messaging.gateway;
import com.forumx.messaging.dto.EventEnvelope; import org.springframework.amqp.rabbit.connection.CorrelationData;
public interface EventGateway { void publish(String exchange, String routingKey, EventEnvelope<?> event); void publish(String exchange, String routingKey, EventEnvelope<?> event, CorrelationData correlationData); }

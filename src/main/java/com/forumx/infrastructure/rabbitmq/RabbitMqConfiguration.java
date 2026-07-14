package com.forumx.infrastructure.rabbitmq;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
@EnableConfigurationProperties(RabbitMqConfiguration.RabbitMqProperties.class)
@org.springframework.context.annotation.Profile("!dev")
public class RabbitMqConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqConfiguration.class);

    @Bean
    public TopicExchange rabbitMainExchange(RabbitMqProperties properties) {
        return new TopicExchange(properties.exchange().name(), true, false);
    }

    @Bean
    public DirectExchange rabbitRetryExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.retryExchange().name(), true, false);
    }

    @Bean
    public DirectExchange rabbitDeadLetterExchange(RabbitMqProperties properties) {
        return new DirectExchange(properties.deadLetterExchange().name(), true, false);
    }

    @Bean
    public Queue rabbitNotificationQueue(RabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queues().notification())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", properties.retryExchange().name(),
                        "x-dead-letter-routing-key", properties.routingKeys().retry()
                ))
                .build();
    }

    @Bean
    public Queue rabbitEmailQueue(RabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queues().email())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", properties.retryExchange().name(),
                        "x-dead-letter-routing-key", properties.routingKeys().retry()
                ))
                .build();
    }

    @Bean
    public Queue rabbitRetryQueue(RabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queues().retry())
                .withArguments(Map.of(
                        "x-message-ttl", (int) properties.retryTtl().toMillis(),
                        "x-dead-letter-exchange", properties.deadLetterExchange().name(),
                        "x-dead-letter-routing-key", properties.routingKeys().deadLetter()
                ))
                .build();
    }

    @Bean
    public Queue rabbitDeadLetterQueue(RabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queues().deadLetter()).build();
    }

    @Bean
    public Declarables rabbitDeclarables(
            TopicExchange rabbitMainExchange,
            DirectExchange rabbitRetryExchange,
            DirectExchange rabbitDeadLetterExchange,
            Queue rabbitNotificationQueue,
            Queue rabbitEmailQueue,
            Queue rabbitRetryQueue,
            Queue rabbitDeadLetterQueue,
            RabbitMqProperties properties
    ) {
        Binding notificationBinding = BindingBuilder.bind(rabbitNotificationQueue)
                .to(rabbitMainExchange)
                .with(properties.routingKeys().notification());

        Binding emailBinding = BindingBuilder.bind(rabbitEmailQueue)
                .to(rabbitMainExchange)
                .with(properties.routingKeys().email());

        Binding retryBinding = BindingBuilder.bind(rabbitRetryQueue)
                .to(rabbitRetryExchange)
                .with(properties.routingKeys().retry());

        Binding deadLetterBinding = BindingBuilder.bind(rabbitDeadLetterQueue)
                .to(rabbitDeadLetterExchange)
                .with(properties.routingKeys().deadLetter());

        return new Declarables(
                rabbitMainExchange,
                rabbitRetryExchange,
                rabbitDeadLetterExchange,
                rabbitNotificationQueue,
                rabbitEmailQueue,
                rabbitRetryQueue,
                rabbitDeadLetterQueue,
                notificationBinding,
                emailBinding,
                retryBinding,
                deadLetterBinding
        );
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(this::logConfirm);
        rabbitTemplate.setReturnsCallback(this::logReturn);
        return rabbitTemplate;
    }

    private void logConfirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            LOGGER.debug("RabbitMQ publish confirmed: correlationId={}", correlationData != null ? correlationData.getId() : "n/a");
            return;
        }

        LOGGER.warn("RabbitMQ publish failed: correlationId={}, cause={}",
                correlationData != null ? correlationData.getId() : "n/a",
                cause);
    }

    private void logReturn(ReturnedMessage returnedMessage) {
        LOGGER.warn("RabbitMQ returned message: exchange={}, routingKey={}, replyCode={}, replyText={}",
                returnedMessage.getExchange(),
                returnedMessage.getRoutingKey(),
                returnedMessage.getReplyCode(),
                returnedMessage.getReplyText());
    }

    @ConfigurationProperties(prefix = "app.rabbitmq")
    public record RabbitMqProperties(
            Exchange exchange,
            Exchange retryExchange,
            Exchange deadLetterExchange,
            Queues queues,
            RoutingKeys routingKeys,
            Duration retryTtl
    ) {
        public record Exchange(String name) {
        }

        public record Queues(String notification, String email, String retry, String deadLetter) {
        }

        public record RoutingKeys(String notification, String email, String retry, String deadLetter) {
        }
    }
}

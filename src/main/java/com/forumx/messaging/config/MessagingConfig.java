package com.forumx.messaging.config;

import com.forumx.messaging.properties.MessagingProperties;
import com.forumx.messaging.serializer.MessageSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@RequiredArgsConstructor
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class MessagingConfig {

    private final MessagingProperties properties;

    public static final String RETRY_EXCHANGE = "forumx.retry.exchange";
    public static final String RETRY_QUEUE = "forumx.notification.retry.queue";
    public static final String RETRY_ROUTING_KEY = "forumx.retry";

    public static final String DLX_EXCHANGE = "forumx.dlx.exchange";
    public static final String DEAD_LETTER_QUEUE = "forumx.notification.dead.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "forumx.dead-letter";

    @Bean
    public CachingConnectionFactory messagingConnectionFactory() {
        CachingConnectionFactory f = new CachingConnectionFactory(properties.getHost(), properties.getPort());
        f.setUsername(properties.getUsername());
        f.setPassword(properties.getPassword());
        f.setVirtualHost(properties.getVirtualHost());
        f.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        f.setPublisherReturns(properties.isPublisherReturns());
        if (properties.isSsl()) {
            try {
                f.getRabbitConnectionFactory().useSslProtocol();
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL for RabbitMQ CachingConnectionFactory", e);
            }
        }
        return f;
    }

    @Bean
    public Jackson2JsonMessageConverter messagingMessageConverter() {
        return new Jackson2JsonMessageConverter(MessageSerializer.objectMapper());
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate t = new RabbitTemplate(factory);
        t.setMessageConverter(converter);
        t.setMandatory(properties.isPublisherReturns());
        return t;
    }

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            @org.springframework.beans.factory.annotation.Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory =
                new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory factory) {
        return new RabbitAdmin(factory);
    }

    @Bean
    public Declarables messagingDeclarables() {
        MessagingProperties.RabbitMq.Exchanges exchanges = properties.getRabbitmq().getExchanges();
        MessagingProperties.RabbitMq.Queues queues = properties.getRabbitmq().getQueues();
        MessagingProperties.RabbitMq.RoutingKeys routingKeys = properties.getRabbitmq().getRoutingKeys();

        // Topic Exchanges
        TopicExchange systemExchange = new TopicExchange(exchanges.getSystem(), true, false);
        TopicExchange userExchange = new TopicExchange(exchanges.getUser(), true, false);
        TopicExchange notificationExchange = new TopicExchange(exchanges.getNotification(), true, false);
        TopicExchange supportExchange = new TopicExchange(exchanges.getSupport(), true, false);

        // Direct Exchanges for Retry and DLX
        DirectExchange retryExchange = new DirectExchange(exchanges.getRetry(), true, false);
        DirectExchange dlxExchange = new DirectExchange(exchanges.getDlx(), true, false);

        // User Notification Queue & Binding
        Queue userNotificationQueue = QueueBuilder.durable(queues.getUserNotification()).build();
        Binding userNotificationBinding = BindingBuilder.bind(userNotificationQueue)
                .to(userExchange)
                .with(routingKeys.getNotificationCreated());

        // Email Queue & Binding
        Queue emailQueue = QueueBuilder.durable(queues.getEmail()).build();
        Binding emailBinding = BindingBuilder.bind(emailQueue)
                .to(notificationExchange)
                .with(routingKeys.getEmailSend());

        // Support & Chat Queues & Bindings
        Queue supportQueue = QueueBuilder.durable(queues.getSupport()).build();
        Binding supportBinding = BindingBuilder.bind(supportQueue)
                .to(supportExchange)
                .with("support.ticket.#");

        Queue chatQueue = QueueBuilder.durable(queues.getChat()).build();
        Binding chatBinding = BindingBuilder.bind(chatQueue)
                .to(supportExchange)
                .with("chat.#");

        // Retry Queue (Dead letters to notification exchange) & Binding
        Queue retryQueue = QueueBuilder.durable(queues.getRetry())
                .withArgument("x-dead-letter-exchange", exchanges.getNotification())
                .withArgument("x-dead-letter-routing-key", routingKeys.getEmailSend())
                .build();
        Binding retryBinding = BindingBuilder.bind(retryQueue)
                .to(retryExchange)
                .with(routingKeys.getDeliveryRetry());

        // Dead Letter Queue & Binding
        Queue deadLetterQueue = QueueBuilder.durable(queues.getDeadLetter()).build();
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(dlxExchange)
                .with(routingKeys.getDeliveryDead());

        return new Declarables(
                systemExchange,
                userExchange,
                notificationExchange,
                supportExchange,
                retryExchange,
                dlxExchange,
                userNotificationQueue,
                userNotificationBinding,
                emailQueue,
                emailBinding,
                supportQueue,
                supportBinding,
                chatQueue,
                chatBinding,
                retryQueue,
                retryBinding,
                deadLetterQueue,
                deadLetterBinding
        );
    }
}

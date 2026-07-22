package com.forumx.messaging.config;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.constant.MessagingRoutingKeys;
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
@RequiredArgsConstructor
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class MessagingConfig {

    private final MessagingProperties properties;

    public static final String RETRY_EXCHANGE = "forumx.retry.exchange";
    public static final String RETRY_QUEUE = "forumx.retry.queue";
    public static final String RETRY_ROUTING_KEY = "forumx.retry";

    public static final String DLX_EXCHANGE = "forumx.dlx.exchange";
    public static final String DEAD_LETTER_QUEUE = "forumx.dead-letter.queue";
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
    public RabbitAdmin rabbitAdmin(ConnectionFactory factory) {
        return new RabbitAdmin(factory);
    }

    @Bean
    public Declarables messagingDeclarables() {
        TopicExchange systemExchange = new TopicExchange(MessagingExchanges.SYSTEM_EXCHANGE, true, false);
        TopicExchange userExchange = new TopicExchange(MessagingExchanges.USER_EXCHANGE, true, false);
        TopicExchange notificationExchange = new TopicExchange(MessagingExchanges.NOTIFICATION_EXCHANGE, true, false);
        TopicExchange supportExchange = new TopicExchange(MessagingExchanges.SUPPORT_EVENTS_EXCHANGE, true, false);

        TopicExchange forumxNotificationExchange = new TopicExchange("forumx.notification.exchange", true, false);
        Queue forumxNotificationQueue = QueueBuilder.durable("forumx.notification.queue").build();
        Binding forumxNotificationBinding = BindingBuilder.bind(forumxNotificationQueue)
                .to(forumxNotificationExchange)
                .with("notification.email.send");

        // Retry & Dead Letter Topology
        DirectExchange retryExchange = new DirectExchange(RETRY_EXCHANGE, true, false);
        Queue retryQueue = QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", "forumx.notification.exchange")
                .withArgument("x-dead-letter-routing-key", "notification.email.send")
                .build();
        Binding retryBinding = BindingBuilder.bind(retryQueue).to(retryExchange).with(RETRY_ROUTING_KEY);

        DirectExchange dlxExchange = new DirectExchange(DLX_EXCHANGE, true, false);
        Queue deadLetterQueue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(dlxExchange).with(DEAD_LETTER_ROUTING_KEY);

        Queue supportQueue = QueueBuilder.durable(MessagingQueues.SUPPORT_QUEUE)
                .withArgument("x-dead-letter-exchange", MessagingExchanges.SUPPORT_EVENTS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MessagingQueues.SUPPORT_DLQ)
                .build();
        Queue supportDlq = QueueBuilder.durable(MessagingQueues.SUPPORT_DLQ).build();
        Binding supportBinding = BindingBuilder.bind(supportQueue).to(supportExchange).with("support.ticket.#");

        Queue chatQueue = QueueBuilder.durable(MessagingQueues.CHAT_QUEUE)
                .withArgument("x-dead-letter-exchange", MessagingExchanges.SUPPORT_EVENTS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MessagingQueues.CHAT_DLQ)
                .build();
        Queue chatDlq = QueueBuilder.durable(MessagingQueues.CHAT_DLQ).build();
        Binding chatBinding = BindingBuilder.bind(chatQueue).to(supportExchange).with("chat.#");

        Queue emailQueue = QueueBuilder.durable(MessagingQueues.EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", MessagingExchanges.NOTIFICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MessagingQueues.EMAIL_DLQ)
                .build();
        Queue emailDlq = QueueBuilder.durable(MessagingQueues.EMAIL_DLQ).build();
        Binding emailBinding = BindingBuilder.bind(emailQueue).to(notificationExchange).with("notification.email.send");

        Queue deliveryRetryQueue = QueueBuilder.durable(MessagingQueues.DELIVERY_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", MessagingExchanges.NOTIFICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MessagingQueues.DELIVERY_RETRY_DLQ)
                .build();
        Queue deliveryRetryDlq = QueueBuilder.durable(MessagingQueues.DELIVERY_RETRY_DLQ).build();
        Binding deliveryRetryBinding = BindingBuilder.bind(deliveryRetryQueue).to(notificationExchange).with(MessagingRoutingKeys.DELIVERY_RETRY);

        Queue deliveryDeadQueue = QueueBuilder.durable(MessagingQueues.DELIVERY_DEAD_QUEUE).build();
        Binding deliveryDeadBinding = BindingBuilder.bind(deliveryDeadQueue).to(notificationExchange).with(MessagingRoutingKeys.DELIVERY_DEAD);

        return new Declarables(
                systemExchange,
                userExchange,
                notificationExchange,
                supportExchange,
                supportQueue,
                supportDlq,
                supportBinding,
                chatQueue,
                chatDlq,
                chatBinding,
                emailQueue,
                emailDlq,
                emailBinding,
                forumxNotificationExchange,
                forumxNotificationQueue,
                forumxNotificationBinding,
                retryExchange,
                retryQueue,
                retryBinding,
                dlxExchange,
                deadLetterQueue,
                deadLetterBinding,
                deliveryRetryQueue,
                deliveryRetryDlq,
                deliveryRetryBinding,
                deliveryDeadQueue,
                deliveryDeadBinding
        );
    }
}

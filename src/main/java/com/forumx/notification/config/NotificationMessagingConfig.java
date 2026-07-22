package com.forumx.notification.config;

import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingQueues;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "forumx.messaging.enabled", havingValue = "true")
public class NotificationMessagingConfig {

    @Bean
    public Queue userNotificationQueue() {
        return QueueBuilder.durable(MessagingQueues.USER_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", MessagingQueues.USER_NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue userNotificationDlq() {
        return QueueBuilder.durable(MessagingQueues.USER_NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding userNotificationBinding(Queue userNotificationQueue) {
        return BindingBuilder.bind(userNotificationQueue)
                .to(new TopicExchange(MessagingExchanges.USER_EVENTS_EXCHANGE))
                .with(MessagingRoutingKeys.NOTIFICATION_CREATED);
    }
}

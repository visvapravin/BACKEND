package com.forumx.messaging.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "forumx.messaging")
public class MessagingProperties {

    private boolean enabled = true;
    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
    private String virtualHost = "/";
    private boolean ssl;
    private String publisherConfirmType = "correlated";
    private boolean publisherReturns = true;
    private Retry retry = new Retry();
    private int prefetch = 10;
    private int concurrency = 1;

    private RabbitMq rabbitmq = new RabbitMq();

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofSeconds(1);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class RabbitMq {
        private Exchanges exchanges = new Exchanges();
        private Queues queues = new Queues();
        private RoutingKeys routingKeys = new RoutingKeys();

        @Getter
        @Setter
        public static class Exchanges {
            private String system = "forumx.events.system";
            private String user = "forumx.events.user";
            private String notification = "forumx.events.notification";
            private String support = "forumx.events.support";
            private String retry = "forumx.retry.exchange";
            private String dlx = "forumx.dlx.exchange";
        }

        @Getter
        @Setter
        public static class Queues {
            private String userNotification = "forumx.user.notification.queue";
            private String email = "forumx.notification.email.queue";
            private String retry = "forumx.notification.retry.queue";
            private String deadLetter = "forumx.notification.dead.queue";
            private String support = "forumx.support.queue";
            private String chat = "forumx.chat.queue";
        }

        @Getter
        @Setter
        public static class RoutingKeys {
            private String notificationCreated = "notification.created";
            private String emailSend = "notification.email.send";
            private String deliveryRetry = "forumx.retry";
            private String deliveryDead = "forumx.dead-letter";
        }
    }
}

package com.forumx.messaging.properties;
import java.time.Duration;
import lombok.Getter; import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Getter @Setter @ConfigurationProperties(prefix = "forumx.messaging")
public class MessagingProperties {
    private boolean enabled = true; private String host = "localhost"; private int port = 5672;
    private String username = "guest"; private String password = "guest"; private String virtualHost = "/";
    private boolean ssl; private String publisherConfirmType = "correlated"; private boolean publisherReturns = true;
    private Retry retry = new Retry(); private int prefetch = 10; private int concurrency = 1;
    @Getter @Setter public static class Retry { private int maxAttempts = 3; private Duration initialInterval = Duration.ofSeconds(1); private double multiplier = 2.0; private Duration maxInterval = Duration.ofSeconds(30); }
}

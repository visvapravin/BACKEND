package com.forumx.notification.delivery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "forumx.delivery.retry")
public class DeliveryRetryProperties {
    private int maxAttempts = 4;
    private long attempt1DelaySeconds = 30;
    private long attempt2DelaySeconds = 120;
    private long attempt3DelaySeconds = 600;
}

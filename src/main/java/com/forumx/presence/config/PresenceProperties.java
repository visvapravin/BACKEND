package com.forumx.presence.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "forumx.presence")
public class PresenceProperties {
    private boolean enabled = true;
    private Duration ttl = Duration.ofMinutes(5);
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private boolean broadcastEnabled = true;
}

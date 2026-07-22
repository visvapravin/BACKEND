package com.forumx.notification.email.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mail")
public class MailProperties {
    private String host = "localhost";
    private int port = 1025;
    private String username;
    private String password;
    private String from = "noreply@forumx.com";
    private boolean enabled = true;
}

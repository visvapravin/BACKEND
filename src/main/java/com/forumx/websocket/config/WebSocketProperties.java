package com.forumx.websocket.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "forumx.websocket")
public class WebSocketProperties {
    private List<String> allowedOrigins = List.of("http://localhost:5173");
}

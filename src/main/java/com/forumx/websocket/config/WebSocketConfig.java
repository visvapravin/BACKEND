package com.forumx.websocket.config;

import com.forumx.websocket.constant.WebSocketDestinations;
import com.forumx.websocket.security.AuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authChannelInterceptor;
    private final WebSocketProperties webSocketProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = webSocketProperties.getAllowedOrigins().toArray(new String[0]);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(WebSocketDestinations.APP_PREFIX);
        registry.setUserDestinationPrefix(WebSocketDestinations.USER_PREFIX);
        
        // NOTE: In-memory SimpleBroker is temporary for this MVP.
        // It will be replaced by a full production Broker Relay (e.g. RabbitMQ/Redis) in future releases.
        registry.enableSimpleBroker(WebSocketDestinations.TOPIC_PREFIX, WebSocketDestinations.QUEUE_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}

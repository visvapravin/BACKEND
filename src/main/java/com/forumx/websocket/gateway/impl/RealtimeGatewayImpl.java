package com.forumx.websocket.gateway.impl;

import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeGatewayImpl implements RealtimeGateway {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public <T> void sendToUser(String username, String destination, RealtimeEvent<T> event) {
        log.debug("Sending real-time message to user '{}' at destination '{}' with event ID '{}'", username, destination, event.getEventId());
        messagingTemplate.convertAndSendToUser(username, destination, event);
    }

    @Override
    public <T> void sendToTopic(String destination, RealtimeEvent<T> event) {
        log.debug("Sending real-time message to topic destination '{}' with event ID '{}'", destination, event.getEventId());
        messagingTemplate.convertAndSend(destination, event);
    }
}

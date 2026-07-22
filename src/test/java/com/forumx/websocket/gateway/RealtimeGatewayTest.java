package com.forumx.websocket.gateway;

import static org.mockito.Mockito.*;

import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.impl.RealtimeGatewayImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
public class RealtimeGatewayTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RealtimeGatewayImpl realtimeGateway;

    @Test
    public void testSendToUserDelegation() {
        // Given
        String username = "user123";
        String destination = "/queue/notifications";
        RealtimeEvent<String> event = RealtimeEvent.<String>builder()
                .type("NOTIFICATION")
                .payload("Hello user")
                .build();

        // When
        realtimeGateway.sendToUser(username, destination, event);

        // Then
        verify(messagingTemplate, times(1)).convertAndSendToUser(username, destination, event);
    }

    @Test
    public void testSendToTopicDelegation() {
        // Given
        String destination = "/topic/notifications";
        RealtimeEvent<String> event = RealtimeEvent.<String>builder()
                .type("BROADCAST")
                .payload("Hello all")
                .build();

        // When
        realtimeGateway.sendToTopic(destination, event);

        // Then
        verify(messagingTemplate, times(1)).convertAndSend(destination, event);
    }
}

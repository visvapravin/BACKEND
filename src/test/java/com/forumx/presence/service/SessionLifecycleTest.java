package com.forumx.presence.service;

import static org.mockito.Mockito.*;

import com.forumx.websocket.listener.SessionListener;
import com.forumx.websocket.session.WebSocketSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.CloseStatus;

import java.util.HashMap;
import java.util.Map;

public class SessionLifecycleTest {

    private PresenceService presenceService;
    private SessionListener sessionListener;

    @BeforeEach
    public void setUp() {
        presenceService = mock(PresenceService.class);
        sessionListener = new SessionListener(presenceService);
    }

    @Test
    public void testSessionConnectLifecycle() {
        Map<String, Object> sessionAttributes = new HashMap<>();
        WebSocketSessionContext context = WebSocketSessionContext.builder()
                .sessionId("session-123")
                .userId(5L)
                .username("test_lifecycle_user")
                .tenantId(1L)
                .build();
        sessionAttributes.put(WebSocketSessionContext.SESSION_KEY, context);

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId("session-123");
        accessor.setSessionAttributes(sessionAttributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionConnectEvent connectEvent = new SessionConnectEvent(this, message);
        sessionListener.handleSessionConnect(connectEvent);

        verify(presenceService, times(1)).markOnline(5L, "test_lifecycle_user", 1L, "session-123");
    }

    @Test
    public void testSessionDisconnectLifecycle() {
        Map<String, Object> sessionAttributes = new HashMap<>();
        WebSocketSessionContext context = WebSocketSessionContext.builder()
                .sessionId("session-123")
                .userId(5L)
                .username("test_lifecycle_user")
                .tenantId(1L)
                .build();
        sessionAttributes.put(WebSocketSessionContext.SESSION_KEY, context);

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId("session-123");
        accessor.setSessionAttributes(sessionAttributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionDisconnectEvent disconnectEvent = new SessionDisconnectEvent(this, message, "session-123", CloseStatus.NORMAL);
        sessionListener.handleSessionDisconnect(disconnectEvent);

        verify(presenceService, times(1)).markOffline(5L, "session-123");
    }
}

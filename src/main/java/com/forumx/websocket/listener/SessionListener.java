package com.forumx.websocket.listener;

import com.forumx.presence.service.PresenceService;
import com.forumx.websocket.session.WebSocketSessionContext;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        WebSocketSessionContext context = sessionAttributes != null 
                ? (WebSocketSessionContext) sessionAttributes.get(WebSocketSessionContext.SESSION_KEY) 
                : null;

        String username = context != null ? context.getUsername() : "Anonymous";
        Long tenantId = context != null ? context.getTenantId() : null;

        log.info("WebSocket connection established. SessionId={}, User={}, TenantId={}", sessionId, username, tenantId);

        if (context != null && context.getUserId() != null) {
            presenceService.markOnline(context.getUserId(), context.getUsername(), context.getTenantId(), sessionId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        WebSocketSessionContext context = sessionAttributes != null 
                ? (WebSocketSessionContext) sessionAttributes.get(WebSocketSessionContext.SESSION_KEY) 
                : null;

        Principal user = accessor.getUser();
        String username = user != null ? user.getName() : (context != null ? context.getUsername() : "Anonymous");
        log.info("WebSocket connection closed. SessionId={}, User={}", sessionId, username);

        if (context != null && context.getUserId() != null) {
            presenceService.markOffline(context.getUserId(), sessionId);
        } else {
            // Fall back to mapping session presence via stored session ID key
            presenceService.markOfflineBySessionId(sessionId);
        }
    }
}

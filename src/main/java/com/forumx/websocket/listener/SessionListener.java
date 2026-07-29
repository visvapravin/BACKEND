package com.forumx.websocket.listener;

import com.forumx.presence.service.PresenceService;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.websocket.session.WebSocketSessionContext;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        processConnect(event.getMessage(), "SessionConnectEvent");
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        processConnect(event.getMessage(), "SessionConnectedEvent");
    }

    private void processConnect(Message<?> message, String eventType) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String sessionId = accessor.getSessionId();

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        WebSocketSessionContext context = sessionAttributes != null
                ? (WebSocketSessionContext) sessionAttributes.get(WebSocketSessionContext.SESSION_KEY)
                : null;

        if (context == null && accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof CustomUserDetails details) {
            context = WebSocketSessionContext.builder()
                    .sessionId(sessionId)
                    .userId(details.getUserId())
                    .username(details.getUsername())
                    .tenantId(details.getTenantId())
                    .build();
            if (sessionAttributes != null) {
                sessionAttributes.put(WebSocketSessionContext.SESSION_KEY, context);
            }
        }

        String username = context != null ? context.getUsername() : (accessor.getUser() != null ? accessor.getUser().getName() : "Anonymous");
        Long tenantId = context != null ? context.getTenantId() : null;

        log.info("WebSocket {} established. SessionId={}, User={}, TenantId={}", eventType, sessionId, username, tenantId);

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

        if (context == null && accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof CustomUserDetails details) {
            context = WebSocketSessionContext.builder()
                    .sessionId(sessionId)
                    .userId(details.getUserId())
                    .username(details.getUsername())
                    .tenantId(details.getTenantId())
                    .build();
        }

        Principal user = accessor.getUser();
        String username = user != null ? user.getName() : (context != null ? context.getUsername() : "Anonymous");
        log.info("WebSocket connection closed. SessionId={}, User={}", sessionId, username);

        if (context != null && context.getUserId() != null) {
            presenceService.markOffline(context.getUserId(), sessionId);
        } else {
            presenceService.markOfflineBySessionId(sessionId);
        }
    }
}

package com.forumx.support.chat.controller;

import com.forumx.support.chat.dto.request.TypingPayload;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.event.ephemeral.TypingStartedEvent;
import com.forumx.support.chat.event.ephemeral.TypingStoppedEvent;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.service.ChatPermissionService;
import com.forumx.websocket.session.WebSocketSessionContext;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatPermissionService chatPermissionService;
    private final ApplicationEventPublisher eventPublisher;

    @MessageMapping("/chat/{sessionId}/typing")
    public void handleTyping(
            @DestinationVariable Long sessionId,
            @Payload TypingPayload payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.warn("STOMP typing event received without session attributes. sessionId={}", sessionId);
            throw new AccessDeniedException("Unauthorized");
        }

        WebSocketSessionContext context = (WebSocketSessionContext) sessionAttributes.get(WebSocketSessionContext.SESSION_KEY);
        if (context == null) {
            log.warn("STOMP typing event received without WebSocketSessionContext. sessionId={}", sessionId);
            throw new AccessDeniedException("Unauthorized");
        }

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Chat session not found with ID: " + sessionId));

        if (!session.getTenant().getId().equals(context.getTenantId())) {
            log.warn("STOMP typing tenant mismatch. contextTenantId={}, sessionTenantId={}", 
                    context.getTenantId(), session.getTenant().getId());
            throw new AccessDeniedException("Unauthorized");
        }

        // Checks participant access and that the session is ACTIVE
        chatPermissionService.assertCanSend(session, context.getUserId());

        if (payload != null && "START".equalsIgnoreCase(payload.getAction())) {
            eventPublisher.publishEvent(new TypingStartedEvent(
                    session.getId(),
                    session.getTenant().getId(),
                    context.getUserId(),
                    context.getUsername()
            ));
        } else if (payload != null && "STOP".equalsIgnoreCase(payload.getAction())) {
            eventPublisher.publishEvent(new TypingStoppedEvent(
                    session.getId(),
                    session.getTenant().getId(),
                    context.getUserId(),
                    context.getUsername()
            ));
        }
    }
}

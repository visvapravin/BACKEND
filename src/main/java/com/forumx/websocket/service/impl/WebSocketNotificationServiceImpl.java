package com.forumx.websocket.service.impl;

import com.forumx.websocket.dto.WebSocketNotificationDto;
import com.forumx.websocket.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationServiceImpl implements WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendNotification(String username, WebSocketNotificationDto dto) {
        if (username == null || username.isBlank() || dto == null) {
            log.warn("Cannot dispatch WebSocket notification: username or DTO is null");
            return;
        }

        String destination = "/queue/notifications";
        try {
            log.info("Pushing WebSocket notification to user={}, notificationId={}, destination=/user/{}{}",
                    username, dto.notificationId(), username, destination);
            messagingTemplate.convertAndSendToUser(username, destination, dto);
            log.info("Successfully pushed WebSocket notification to user={}", username);
        } catch (Exception e) {
            log.error("Failed to push WebSocket notification to user={}: {}", username, e.getMessage(), e);
        }
    }
}

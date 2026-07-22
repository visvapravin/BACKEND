package com.forumx.websocket.dto;

import java.time.Instant;

public record WebSocketNotificationDto(
        Long notificationId,
        String title,
        String message,
        String type,
        Instant createdAt,
        boolean isRead
) {}

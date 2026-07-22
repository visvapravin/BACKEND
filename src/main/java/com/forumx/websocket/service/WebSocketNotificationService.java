package com.forumx.websocket.service;

import com.forumx.websocket.dto.WebSocketNotificationDto;

public interface WebSocketNotificationService {
    void sendNotification(String username, WebSocketNotificationDto dto);
}

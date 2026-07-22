package com.forumx.websocket.controller;

import com.forumx.notification.dto.NotificationEvent;
import com.forumx.notification.publisher.NotificationPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for testing WebSocket end-to-end notification delivery. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test/websocket-notification")
@Tag(name = "WebSocket Testing", description = "Endpoints for testing real-time STOMP WebSocket notification delivery.")
public class TestWebSocketNotificationController {

    private final NotificationPublisher notificationPublisher;

    @PostMapping
    @Operation(
            summary = "Trigger STOMP WebSocket notification test",
            description = "Publishes a notification event to RabbitMQ. The consumer persists it to Neon PostgreSQL and dispatches real-time STOMP WebSocket frames to /user/queue/notifications."
    )
    public ResponseEntity<Void> sendTestWebSocketNotification(@RequestBody NotificationEvent event) {
        log.info("Triggering WebSocket test notification publish for recipient={}", event.email());
        notificationPublisher.publish(event);
        return ResponseEntity.ok().build();
    }
}

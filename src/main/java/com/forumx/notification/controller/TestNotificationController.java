package com.forumx.notification.controller;

import com.forumx.notification.dto.NotificationEvent;
import com.forumx.notification.publisher.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestNotificationController {

    private final NotificationPublisher notificationPublisher;

    public record TestNotificationRequest(
            Long tenantId,
            Long userId,
            String email,
            String subject,
            String body,
            String type
    ) {}

    @PostMapping("/notification")
    public ResponseEntity<Void> testPublish(@RequestBody TestNotificationRequest request) {
        log.info("Test notification publish requested for {}", request.email());
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                request.tenantId() != null ? request.tenantId() : 1L,
                request.userId() != null ? request.userId() : 1L,
                request.email(),
                request.subject(),
                request.body(),
                request.type() != null ? request.type() : "TEST",
                Instant.now()
        );
        notificationPublisher.publish(event);
        return ResponseEntity.ok().build();
    }
}

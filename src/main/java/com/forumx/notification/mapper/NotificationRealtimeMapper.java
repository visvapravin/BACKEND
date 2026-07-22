package com.forumx.notification.mapper;

import com.forumx.notification.dto.response.NotificationResponse;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.websocket.dto.RealtimeEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationRealtimeMapper {

    public RealtimeEvent<NotificationResponse> toRealtimeEvent(NotificationCreatedEvent event) {
        NotificationResponse response = NotificationResponse.builder()
                .id(event.notificationId())
                .recipientId(event.recipientUserId())
                .actorId(event.actorId())
                .notificationType(event.type() != null ? NotificationType.valueOf(event.type()) : null)
                .title(event.title())
                .message(event.message())
                .referenceType(event.referenceType() != null ? ReferenceType.valueOf(event.referenceType()) : null)
                .referenceId(event.referenceId())
                .read(false)
                .readAt(null)
                .createdAt(event.createdAt())
                .build();

        return RealtimeEvent.<NotificationResponse>builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .type("NOTIFICATION_CREATED")
                .payload(response)
                .build();
    }
}

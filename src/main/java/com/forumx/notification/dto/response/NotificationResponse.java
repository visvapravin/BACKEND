package com.forumx.notification.dto.response;

import java.time.Instant;

import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private NotificationType notificationType;
    private String title;
    private String message;
    private ReferenceType referenceType;
    private Long referenceId;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}

package com.forumx.notification.preference.dto.request;

import com.forumx.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull NotificationType notificationType,
        Boolean emailEnabled,
        Boolean webSocketEnabled,
        Boolean pushEnabled,
        Boolean digestEnabled
) {}

package com.forumx.notification.preference.dto.response;

import com.forumx.notification.entity.NotificationType;

public record NotificationPreferenceResponse(
        Long id,
        Long tenantId,
        Long userId,
        NotificationType notificationType,
        boolean emailEnabled,
        boolean webSocketEnabled,
        boolean pushEnabled,
        boolean digestEnabled
) {}

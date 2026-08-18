package com.forumx.notification.preference.service;

import java.util.List;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.preference.dto.request.UpdateNotificationPreferenceRequest;
import com.forumx.notification.preference.dto.response.NotificationPreferenceResponse;

public interface NotificationPreferenceService {
    List<NotificationPreferenceResponse> getUserPreferences();
    NotificationPreferenceResponse getEffectivePreference(Long tenantId, Long userId, NotificationType notificationType);
    NotificationPreferenceResponse updatePreference(UpdateNotificationPreferenceRequest request);
    NotificationPreferenceResponse updatePreferenceForType(NotificationType type, UpdateNotificationPreferenceRequest request);
    void resetUserPreferences();
}

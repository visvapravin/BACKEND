package com.forumx.notification.dto.response;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last,
        long unreadCount
) {}

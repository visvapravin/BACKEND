package com.forumx.moderation.dto.response;

import com.forumx.moderation.entity.ModerationTargetType;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import com.forumx.moderation.entity.ReportStatus;
import java.time.Instant;

public record ModerationReportResponse(
        Long id,
        Long tenantId,
        Long reporterId,
        String reporterUsername,
        ModerationTargetType targetType,
        Long targetId,
        ReportReason reason,
        String description,
        ReportStatus status,
        ReportPriority priority,
        int reportCount,
        Long assignedToId,
        String assignedToUsername,
        Instant assignedAt,
        Instant reviewStartedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

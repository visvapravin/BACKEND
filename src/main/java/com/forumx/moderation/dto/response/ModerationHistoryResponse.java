package com.forumx.moderation.dto.response;

import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ReportStatus;
import java.time.Instant;

public record ModerationHistoryResponse(
        Long id,
        Long reportId,
        Long moderatorId,
        String moderatorUsername,
        ModerationAction action,
        ReportStatus previousStatus,
        ReportStatus newStatus,
        String decisionNotes,
        Instant createdAt
) {
}

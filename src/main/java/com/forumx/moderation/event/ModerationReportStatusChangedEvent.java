package com.forumx.moderation.event;

import com.forumx.moderation.entity.ModerationReport;
import com.forumx.moderation.entity.ReportStatus;

public record ModerationReportStatusChangedEvent(
        ModerationReport report,
        ReportStatus previousStatus,
        ReportStatus newStatus
) {
}

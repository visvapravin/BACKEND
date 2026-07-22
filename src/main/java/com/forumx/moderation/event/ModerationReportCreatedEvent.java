package com.forumx.moderation.event;

import com.forumx.moderation.entity.ModerationReport;

public record ModerationReportCreatedEvent(
        ModerationReport report
) {
}

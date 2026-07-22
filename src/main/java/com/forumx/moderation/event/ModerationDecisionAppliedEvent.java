package com.forumx.moderation.event;

import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ModerationReport;

public record ModerationDecisionAppliedEvent(
        ModerationReport report,
        Long moderatorId,
        ModerationAction action,
        String decisionNotes
) {
}

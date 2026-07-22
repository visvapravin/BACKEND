package com.forumx.moderation.engine;

import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ReportPriority;
import com.forumx.moderation.entity.ReportReason;
import org.springframework.stereotype.Component;

@Component
public class ModerationDecisionEngine {

    public ReportPriority evaluatePriority(ReportReason reason) {
        if (reason == null) {
            return ReportPriority.MEDIUM;
        }

        return switch (reason) {
            case SPAM, DUPLICATE -> ReportPriority.LOW;
            case MISINFORMATION, OTHER -> ReportPriority.MEDIUM;
            case ABUSE, COPYRIGHT -> ReportPriority.HIGH;
            case HARASSMENT, HATE_SPEECH -> ReportPriority.CRITICAL;
        };
    }

    public boolean isActionAllowed(ReportReason reason, ModerationAction action) {
        if (reason == null || action == null) {
            return false;
        }

        // Standard safety rule validation
        return switch (action) {
            case DISMISS -> true;
            case WARN_USER, HIDE_CONTENT -> true;
            case DELETE_CONTENT -> reason != ReportReason.DUPLICATE && reason != ReportReason.OTHER;
            case SUSPEND_USER, BAN_USER -> reason == ReportReason.ABUSE 
                                        || reason == ReportReason.HARASSMENT 
                                        || reason == ReportReason.HATE_SPEECH 
                                        || reason == ReportReason.COPYRIGHT;
        };
    }
}

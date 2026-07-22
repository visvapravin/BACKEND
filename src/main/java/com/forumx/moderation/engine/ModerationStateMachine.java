package com.forumx.moderation.engine;

import com.forumx.moderation.entity.ReportStatus;
import com.forumx.moderation.exception.InvalidModerationStateException;
import org.springframework.stereotype.Component;

@Component
public class ModerationStateMachine {

    public void validateTransition(ReportStatus current, ReportStatus target) {


        boolean valid = switch (current) {
            case OPEN -> target == ReportStatus.IN_REVIEW;
            case IN_REVIEW -> target == ReportStatus.RESOLVED || target == ReportStatus.REJECTED;
            case RESOLVED, REJECTED -> false;
        };

        if (!valid) {
            throw new InvalidModerationStateException(
                    "Invalid report status transition from " + current + " to " + target);
        }
    }
}

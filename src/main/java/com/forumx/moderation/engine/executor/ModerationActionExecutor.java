package com.forumx.moderation.engine.executor;

import com.forumx.moderation.entity.ModerationAction;
import com.forumx.moderation.entity.ModerationTarget;

public interface ModerationActionExecutor {
    void execute(Long tenantId, ModerationAction action, ModerationTarget target, String notes);
}

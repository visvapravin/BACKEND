package com.forumx.moderation.dto.request;

import com.forumx.moderation.entity.ModerationAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerationDecisionRequest(
        @NotNull
        ModerationAction action,

        @NotBlank
        @Size(max = 1000)
        String decisionNotes
) {
}

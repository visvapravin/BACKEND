package com.forumx.moderation.dto.request;

import com.forumx.moderation.entity.ModerationTargetType;
import com.forumx.moderation.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull
        ModerationTargetType targetType,

        @NotNull
        Long targetId,

        @NotNull
        ReportReason reason,

        @NotBlank
        @Size(max = 1000)
        String description
) {
}

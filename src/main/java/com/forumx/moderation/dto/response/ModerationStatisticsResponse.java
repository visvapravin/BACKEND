package com.forumx.moderation.dto.response;

import java.util.Map;

public record ModerationStatisticsResponse(
        long openReports,
        long closedReports,
        double averageResolutionTimeMinutes,
        Map<String, Long> reportsByReason,
        Map<String, Long> reportsByModerator,
        Map<String, Long> reportsByTenant
) {
}

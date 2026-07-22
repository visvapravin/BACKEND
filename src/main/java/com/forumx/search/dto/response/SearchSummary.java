package com.forumx.search.dto.response;

public record SearchSummary(
        long totalQuestions,
        long totalAnswers,
        long totalUsers,
        String provider,
        long executionTimeMs
) {
}

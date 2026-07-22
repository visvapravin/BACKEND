package com.forumx.search.domain;

import java.time.Instant;

public record QuestionSearchResult(
        Long id,
        String title,
        String snippet,
        String authorUsername,
        boolean solved,
        int viewCount,
        int answerCount,
        Instant createdAt
) implements SearchResult {

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getType() {
        return "QUESTION";
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }
}

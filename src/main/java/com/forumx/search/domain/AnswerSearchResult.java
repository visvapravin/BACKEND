package com.forumx.search.domain;

import java.time.Instant;

public record AnswerSearchResult(
        Long id,
        Long questionId,
        String snippet,
        String authorUsername,
        Instant createdAt
) implements SearchResult {

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getType() {
        return "ANSWER";
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }
}

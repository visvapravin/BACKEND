package com.forumx.search.domain;

import java.time.Instant;

public record UserSearchResult(
        Long id,
        String username,
        String email,
        boolean active,
        Instant createdAt
) implements SearchResult {

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getType() {
        return "USER";
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }
}

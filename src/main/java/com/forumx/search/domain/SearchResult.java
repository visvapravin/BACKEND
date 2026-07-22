package com.forumx.search.domain;

import java.time.Instant;

public sealed interface SearchResult permits QuestionSearchResult, AnswerSearchResult, UserSearchResult {
    Long getId();
    String getType();
    Instant getCreatedAt();
}

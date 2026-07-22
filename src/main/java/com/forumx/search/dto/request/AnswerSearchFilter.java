package com.forumx.search.dto.request;

import java.time.Instant;

public record AnswerSearchFilter(
        String author,
        Instant createdAfter,
        Instant createdBefore
) {
}

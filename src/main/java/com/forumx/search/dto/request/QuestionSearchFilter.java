package com.forumx.search.dto.request;

import java.time.Instant;

public record QuestionSearchFilter(
        String author,
        Boolean solved,
        Instant createdAfter,
        Instant createdBefore
) {
}

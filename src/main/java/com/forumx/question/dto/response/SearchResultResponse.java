package com.forumx.question.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class SearchResultResponse {
    private Long questionId;
    private String title;
    private String shortDescription;
    private String authorName;
    private Integer voteCount;
    private Integer answerCount;
    private Instant createdAt;

    // Projection constructor matching the JPQL query arguments exactly
    public SearchResultResponse(Long questionId, String title, String content, String authorName, Integer voteCount, Integer answerCount, Instant createdAt) {
        this.questionId = questionId;
        this.title = title;
        this.shortDescription = content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content;
        this.authorName = authorName;
        this.voteCount = voteCount;
        this.answerCount = answerCount;
        this.createdAt = createdAt;
    }
}

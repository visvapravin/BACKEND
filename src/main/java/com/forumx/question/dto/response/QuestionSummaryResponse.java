package com.forumx.question.dto.response;

import java.time.Instant;

import com.forumx.question.entity.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight DTO representing a question summary, typically returned in lists or pagination results.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSummaryResponse {

    /**
     * The ID of the question.
     */
    private Long id;

    /**
     * The title of the question.
     */
    private String title;

    /**
     * The author's user ID.
     */
    private Long authorId;

    /**
     * The author's unique username.
     */
    private String authorUsername;



    /**
     * The status of the question.
     */
    private QuestionStatus status;

    /**
     * The number of times the question has been viewed.
     */
    private Integer viewCount;

    /**
     * The number of answers submitted for the question.
     */
    private Integer answerCount;

    /**
     * The running upvote/downvote score.
     */
    private Integer voteScore;

    /**
     * Whether the question is pinned at the top.
     */
    private boolean pinned;

    /**
     * Whether the question is locked.
     */
    private boolean locked;

    /**
     * The timestamp when the question was created.
     */
    private Instant createdAt;
}

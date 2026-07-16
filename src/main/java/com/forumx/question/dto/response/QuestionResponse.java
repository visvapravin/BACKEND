package com.forumx.question.dto.response;

import java.time.Instant;

import com.forumx.question.entity.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Detailed DTO representing a question, containing full details.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    /**
     * The ID of the question.
     */
    private Long id;

    /**
     * The title of the question.
     */
    private String title;

    /**
     * The content body of the question.
     */
    private String content;

    /**
     * The author's user ID.
     */
    private Long authorId;

    /**
     * The author's unique username.
     */
    private String authorUsername;

    /**
     * The ID of the tenant the question belongs to.
     */
    private Long tenantId;

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
     * Whether the question is pinned.
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

    /**
     * The timestamp when the question was last updated.
     */
    private Instant updatedAt;
}

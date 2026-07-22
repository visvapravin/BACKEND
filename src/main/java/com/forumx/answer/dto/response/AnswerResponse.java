package com.forumx.answer.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO representing an answer, containing key details.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResponse {

    /**
     * The ID of the answer.
     */
    private Long id;

    /**
     * The ID of the question this answer belongs to.
     */
    private Long questionId;

    /**
     * The author's user ID.
     */
    private Long authorId;

    /**
     * The author's unique username.
     */
    private String authorUsername;

    /**
     * The content body of the answer.
     */
    private String content;

    /**
     * The timestamp when the answer was created.
     */
    private Instant createdAt;

    /**
     * The timestamp when the answer was last updated.
     */
    private Instant updatedAt;
}

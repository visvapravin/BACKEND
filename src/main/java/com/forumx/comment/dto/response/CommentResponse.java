package com.forumx.comment.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO representing comment response details.
 * Excludes parentCommentId or deleted flags to keep V19 API clean.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private Long id;
    private Long authorId;
    private String authorUsername;
    private String content;
    private boolean edited;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long questionId;
    private Long answerId;
}

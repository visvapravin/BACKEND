package com.forumx.question.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCreatedEvent {
    private Long questionId;
    private String title;
    private Long authorId;
    private Long tenantId;
    private Instant createdAt;
}

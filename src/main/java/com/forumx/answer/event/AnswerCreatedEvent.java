package com.forumx.answer.event;

/**
 * Record representing the event published when an answer is created.
 */
public record AnswerCreatedEvent(
        Long answerId,
        Long questionId,
        Long authorId,
        Long tenantId
) {}

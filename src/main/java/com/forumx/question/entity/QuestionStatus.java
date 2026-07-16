package com.forumx.question.entity;

/**
 * Represents the status/state of a Question.
 */
public enum QuestionStatus {
    /**
     * The question is open and accepting answers.
     */
    OPEN,

    /**
     * An answer to the question has been accepted.
     */
    ANSWERED,

    /**
     * The question is closed (e.g. off-topic, duplicate, resolved).
     */
    CLOSED,

    /**
     * The question is archived.
     */
    ARCHIVED
}

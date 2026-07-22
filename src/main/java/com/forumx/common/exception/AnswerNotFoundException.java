package com.forumx.common.exception;

import jakarta.persistence.EntityNotFoundException;

/**
 * Exception thrown when a requested Answer is not found.
 */
public class AnswerNotFoundException extends EntityNotFoundException {
    public AnswerNotFoundException(String message) {
        super(message);
    }
}

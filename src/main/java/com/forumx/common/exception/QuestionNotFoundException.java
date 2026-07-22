package com.forumx.common.exception;

import jakarta.persistence.EntityNotFoundException;

/**
 * Exception thrown when a requested Question is not found.
 */
public class QuestionNotFoundException extends EntityNotFoundException {
    public QuestionNotFoundException(String message) {
        super(message);
    }
}

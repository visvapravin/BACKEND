package com.forumx.common.exception;

public class InvalidSearchQueryException extends RuntimeException {
    public InvalidSearchQueryException(String message) {
        super(message);
    }
}

package com.forumx.common.exception;

public class ModeratorNotFoundException extends RuntimeException {
    public ModeratorNotFoundException(String message) {
        super(message);
    }
}

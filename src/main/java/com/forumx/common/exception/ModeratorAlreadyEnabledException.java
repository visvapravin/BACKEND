package com.forumx.common.exception;

public class ModeratorAlreadyEnabledException extends RuntimeException {
    public ModeratorAlreadyEnabledException(String message) {
        super(message);
    }
}

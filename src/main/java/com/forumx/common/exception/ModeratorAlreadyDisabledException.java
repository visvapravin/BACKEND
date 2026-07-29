package com.forumx.common.exception;

public class ModeratorAlreadyDisabledException extends RuntimeException {
    public ModeratorAlreadyDisabledException(String message) {
        super(message);
    }
}

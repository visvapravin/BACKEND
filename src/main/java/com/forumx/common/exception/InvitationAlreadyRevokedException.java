package com.forumx.common.exception;

public class InvitationAlreadyRevokedException extends RuntimeException {
    public InvitationAlreadyRevokedException(String message) {
        super(message);
    }
}

package com.forumx.common.exception;

public class InvitationAlreadyPendingException extends RuntimeException {
    public InvitationAlreadyPendingException(String message) {
        super(message);
    }
}

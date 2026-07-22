package com.forumx.common.exception;

public class TicketClosedException extends RuntimeException {
    public TicketClosedException(String message) {
        super(message);
    }
}

package com.forumx.common.exception;

import jakarta.persistence.EntityNotFoundException;

public class TicketNotFoundException extends EntityNotFoundException {
    public TicketNotFoundException(String message) {
        super(message);
    }
}

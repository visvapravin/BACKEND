package com.forumx.common.exception;

public class InvalidTenantSlugException extends RuntimeException {
    public InvalidTenantSlugException(String message) {
        super(message);
    }
}

package com.forumx.common.exception;

public class TenantSlugAlreadyExistsException extends RuntimeException {
    public TenantSlugAlreadyExistsException(String message) {
        super(message);
    }
}

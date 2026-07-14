package com.forumx.common.exception;

/**
 * Exception thrown when a user with a passwordless Google Sign-In account attempts a local password login.
 */
public class PasswordNotSetException extends RuntimeException {
    
    public PasswordNotSetException(String message) {
        super(message);
    }
}

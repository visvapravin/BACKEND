package com.forumx.common.exception;

/**
 * Exception thrown when the user attempts to reset their password to their current password.
 */
public class PasswordReuseException extends RuntimeException {
    
    public PasswordReuseException(String message) {
        super(message);
    }
}

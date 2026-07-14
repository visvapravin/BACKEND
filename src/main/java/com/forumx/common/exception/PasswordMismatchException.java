package com.forumx.common.exception;

/**
 * Exception thrown when the confirmation password does not match the new password.
 */
public class PasswordMismatchException extends RuntimeException {
    
    public PasswordMismatchException(String message) {
        super(message);
    }
}

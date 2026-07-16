package com.forumx.common.exception;

/**
 * Exception thrown when domain-level data integrity constraints are violated (e.g. database corruption).
 */
public class DomainIntegrityException extends RuntimeException {
    
    public DomainIntegrityException(String message) {
        super(message);
    }
}

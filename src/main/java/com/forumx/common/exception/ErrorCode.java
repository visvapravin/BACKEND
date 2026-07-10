package com.forumx.common.exception;

public enum ErrorCode {

    VALIDATION_ERROR("Validation failed"),
    USER_ALREADY_EXISTS("User already exists"),
    USER_NOT_FOUND("User not found"),
    INVALID_CREDENTIALS("Invalid credentials"),
    ACCESS_DENIED("Access denied"),
    UNAUTHORIZED("Unauthorized"),
    RESOURCE_NOT_FOUND("Resource not found"),
    BAD_REQUEST("Bad request"),
    CONFLICT("Conflict"),
    FORBIDDEN("Forbidden"),
    INTERNAL_SERVER_ERROR("Internal server error"),
    TOKEN_EXPIRED("Token has expired"),
    TOKEN_INVALID("Token is invalid"),
    TENANT_NOT_FOUND("Tenant not found");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

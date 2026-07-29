package com.forumx.common.exception;

public enum ErrorCode {

    VALIDATION_ERROR("Validation failed"),
    USER_ALREADY_EXISTS("User already exists"),
    USERNAME_ALREADY_EXISTS("Username already exists"),
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
    EMAIL_NOT_VERIFIED("Email not verified"),
    TENANT_NOT_FOUND("Tenant not found"),
    PASSWORDS_DO_NOT_MATCH("Passwords do not match"),
    PASSWORD_REUSE("Cannot reset password to the current password"),
    PASSWORD_NOT_SET("This account currently uses Google Sign-In. Create a password to enable email login."),
    DOMAIN_INTEGRITY_VIOLATION("Domain integrity violation or database corruption detected"),
    INVITATION_NOT_FOUND("Invitation not found"),
    INVITATION_EXPIRED("Invitation has expired"),
    INVITATION_REVOKED("Invitation has been revoked"),
    INVITATION_ALREADY_ACCEPTED("Invitation has already been accepted"),
    INVITATION_ALREADY_PENDING("An active invitation already exists for this email"),
    INVITATION_ALREADY_REVOKED("Invitation has already been revoked"),
    EMAIL_ALREADY_REGISTERED("User account with this email already exists"),
    MODERATOR_NOT_FOUND("Moderator not found"),
    MODERATOR_ALREADY_DISABLED("Moderator account is already disabled"),
    MODERATOR_ALREADY_ENABLED("Moderator account is already enabled");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

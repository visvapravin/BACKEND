package com.forumx.mail;

public interface EmailService {

    /**
     * Sends a verification email to a newly registered user.
     *
     * @param toEmail         the user's email address
     * @param username        the user's username
     * @param verificationUrl the full email verification url (including token)
     */
    void sendVerificationEmail(String toEmail, String username, String verificationUrl);

    /**
     * Sends a password reset email to a user.
     *
     * @param toEmail  the user's email address
     * @param username the user's username
     * @param resetUrl the full password reset url (including token)
     */
    void sendPasswordResetEmail(String toEmail, String username, String resetUrl);
}

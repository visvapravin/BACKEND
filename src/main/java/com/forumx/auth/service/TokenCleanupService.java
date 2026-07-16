package com.forumx.auth.service;

import java.time.Instant;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Periodically deletes expired or used tokens. Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "${app.token-cleanup.cron:0 0 2 * * ?}")
    public void cleanupTokens() {
        log.info("Starting scheduled cleanup of verification and password reset tokens...");
        Instant now = Instant.now();
        
        try {
            int deletedVerification = verificationTokenRepository.deleteUsedOrExpiredTokens(now);
            log.info("Cleaned up {} expired/used verification tokens.", deletedVerification);
        } catch (Exception e) {
            log.error("Failed to clean up verification tokens", e);
        }

        try {
            int deletedReset = passwordResetTokenRepository.deleteUsedOrExpiredTokens(now);
            log.info("Cleaned up {} expired/used password reset tokens.", deletedReset);
        } catch (Exception e) {
            log.error("Failed to clean up password reset tokens", e);
        }
        
        log.info("Token cleanup completed.");
    }
}

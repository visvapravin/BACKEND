package com.forumx.auth.passwordreset.repository;

import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.User;
import com.forumx.auth.passwordreset.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for {@link PasswordResetToken} entity.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds a password reset token by its SHA-256 hash value.
     *
     * @param tokenHash the SHA-256 hash of the token
     * @return an Optional containing the found token, or empty if not found
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Finds all active, unused password reset tokens for a specific user.
     *
     * @param user the user whose tokens to find
     * @return list of active, unused password reset tokens
     */
    List<PasswordResetToken> findAllByUserAndUsedFalse(User user);
}

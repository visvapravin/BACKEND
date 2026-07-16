package com.forumx.auth.verification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.User;
import com.forumx.auth.verification.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA Repository for {@link VerificationToken} entity.
 */
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * Finds a verification token by its SHA-256 hash value.
     *
     * @param tokenHash the SHA-256 hash of the token
     * @return an Optional containing the found token, or empty if not found
     */
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Finds all active (unused) verification tokens for a specific user.
     *
     * @param user the user whose unused tokens are to be found
     * @return list of unused verification tokens
     */
    List<VerificationToken> findAllByUserAndUsedFalse(User user);

    /**
     * Deletes verification tokens that are used or have expired.
     *
     * @param now the current instant representing the expiration boundary
     * @return count of deleted tokens
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.used = true OR vt.expiresAt < :now")
    int deleteUsedOrExpiredTokens(@Param("now") Instant now);
}

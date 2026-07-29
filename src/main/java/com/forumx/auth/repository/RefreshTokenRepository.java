package com.forumx.auth.repository;

import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.RefreshToken;
import com.forumx.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for {@link RefreshToken} entity.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Finds a refresh token by its UUID value.
     *
     * @param token the UUID string value
     * @return an Optional containing the found refresh token, or empty if not found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Finds all refresh tokens belonging to a specific user.
     *
     * @param user the user whose refresh tokens to find
     * @return list of refresh tokens for the user
     */
    List<RefreshToken> findAllByUser(User user);

    /**
     * Finds all refresh tokens belonging to a specific user by ID.
     *
     * @param userId the ID of the user
     * @return list of refresh tokens for the user
     */
    List<RefreshToken> findAllByUserId(Long userId);

    /**
     * Bulk-revokes all non-revoked refresh tokens belonging to a user by setting
     * revoked=true and revokedAt=NOW(). Used when disabling a moderator account.
     * Preserves token records for audit history (does not physically delete).
     *
     * @param userId the target user's ID
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true, rt.revokedAt = CURRENT_TIMESTAMP
            WHERE rt.user.id = :userId AND rt.revoked = false
            """)
    void revokeAllByUserId(@Param("userId") Long userId);
}

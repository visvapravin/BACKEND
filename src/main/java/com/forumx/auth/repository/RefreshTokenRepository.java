package com.forumx.auth.repository;

import java.util.Optional;

import com.forumx.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
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
    java.util.List<RefreshToken> findAllByUser(com.forumx.auth.entity.User user);
}

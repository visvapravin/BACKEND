package com.forumx.auth.repository;

import com.forumx.auth.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for {@link UserProfile} entity.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}

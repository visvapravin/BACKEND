package com.forumx.auth.repository;

import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.User;
import com.forumx.auth.entity.User.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByGoogleIdAndDeletedFalse(String googleId);

    boolean existsByGoogleIdAndDeletedFalse(String googleId);

    Optional<User> findByTenantIdAndUsernameAndDeletedFalse(Long tenantId, String username);

    Optional<User> findByTenantIdAndEmailAndDeletedFalse(Long tenantId, String email);

    boolean existsByTenantIdAndUsername(Long tenantId, String username);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    Page<User> findAllByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    List<User> findAllByStatus(UserStatus status);

    Optional<User> findByTenantIdAndUsernameAndEnabledTrue(Long tenantId, String username);

    Optional<User> findByTenantIdAndEmailAndEnabledTrue(Long tenantId, String email);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN u.userProfile p
            WHERE u.tenant.id = :tenantId
              AND u.deleted = false
              AND (
                  LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<User> searchUsers(@Param("tenantId") Long tenantId,
                           @Param("keyword") String keyword,
                           Pageable pageable);

    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.tenant
            LEFT JOIN FETCH u.userProfile
            LEFT JOIN FETCH u.userRoles ur
            LEFT JOIN FETCH ur.role r
            LEFT JOIN FETCH r.rolePermissions rp
            LEFT JOIN FETCH rp.permission
            WHERE u.id = :userId AND u.deleted = false
            """)
    Optional<User> findByIdWithFullProfile(@Param("userId") Long userId);
}

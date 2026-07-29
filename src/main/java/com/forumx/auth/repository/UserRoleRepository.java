package com.forumx.auth.repository;

import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for {@link UserRole} entity.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Returns all active (non-expired) role assignments for a given user,
     * with the Role eagerly fetched.
     * <p>
     * Used by TenantStaffService as the authoritative source for role membership.
     * Do NOT use user.getUserRoles() as the authoritative source — the lazy
     * collection may be stale within the same Hibernate persistence context.
     */
    @Query("""
            SELECT ur FROM UserRole ur
            JOIN FETCH ur.role r
            WHERE ur.user.id = :userId
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<UserRole> findActiveRolesByUserId(@Param("userId") Long userId);

    /**
     * Returns whether a given user has an active MODERATOR account role.
     */
    @Query("""
            SELECT COUNT(ur) > 0 FROM UserRole ur
            JOIN ur.role r
            WHERE ur.user.id = :userId
              AND r.roleName = :roleName
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveRoleByUserIdAndRoleName(@Param("userId") Long userId,
                                                @Param("roleName") RoleType roleName);

    /**
     * Returns IDs of users in the given tenant that have an active MODERATOR account role.
     * Used for paginated moderator listing without incorrect Hibernate pagination on JOIN FETCH.
     */
    @Query("""
            SELECT DISTINCT ur.user.id FROM UserRole ur
            JOIN ur.role r
            WHERE ur.user.tenant.id = :tenantId
              AND r.roleName = :roleName
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
              AND ur.user.deleted = false
            """)
    Page<Long> findUserIdsWithActiveRoleInTenant(@Param("tenantId") Long tenantId,
                                                 @Param("roleName") RoleType roleName,
                                                 Pageable pageable);
}

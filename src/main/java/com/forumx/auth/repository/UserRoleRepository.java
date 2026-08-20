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
import java.util.Optional;

/**
 * Spring Data JPA Repository for {@link UserRole} entity.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Returns all active (non-expired) role assignments for a given user,
     * with the Role eagerly fetched.
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
     * Returns active roles for a user specifically within a target tenant.
     */
    @Query("""
            SELECT ur FROM UserRole ur
            JOIN FETCH ur.role r
            WHERE ur.user.id = :userId
              AND ur.tenant.id = :tenantId
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<UserRole> findActiveRolesByUserIdAndTenantId(@Param("userId") Long userId,
                                                      @Param("tenantId") Long tenantId);

    /**
     * Checks if a user has any active, non-expired role membership within a specific tenant.
     * Efficient boolean count query avoiding full entity hydration.
     */
    @Query("""
            SELECT COUNT(ur) > 0 FROM UserRole ur
            WHERE ur.user.id = :userId
              AND ur.tenant.id = :tenantId
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveMembership(@Param("userId") Long userId,
                                   @Param("tenantId") Long tenantId);

    /**
     * Returns active platform-scoped roles (tenant_id IS NULL) for a user.
     */
    @Query("""
            SELECT ur FROM UserRole ur
            JOIN FETCH ur.role r
            WHERE ur.user.id = :userId
              AND ur.tenant IS NULL
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<UserRole> findActivePlatformRolesByUserId(@Param("userId") Long userId);

    /**
     * Finds role assignment for a user, tenant, and specific role.
     */
    @Query("""
            SELECT ur FROM UserRole ur
            WHERE ur.user.id = :userId
              AND ur.tenant.id = :tenantId
              AND ur.role.id = :roleId
              AND ur.deleted = false
            """)
    Optional<UserRole> findByUserIdAndTenantIdAndRoleId(@Param("userId") Long userId,
                                                        @Param("tenantId") Long tenantId,
                                                        @Param("roleId") Long roleId);

    /**
     * Finds platform role assignment for a user and specific role (tenant is NULL).
     */
    @Query("""
            SELECT ur FROM UserRole ur
            WHERE ur.user.id = :userId
              AND ur.tenant IS NULL
              AND ur.role.id = :roleId
              AND ur.deleted = false
            """)
    Optional<UserRole> findByUserIdAndTenantNullAndRoleId(@Param("userId") Long userId,
                                                          @Param("roleId") Long roleId);

    /**
     * Retrieves all active tenant memberships and associated roles for a user across active tenants.
     */
    @Query("""
            SELECT ur FROM UserRole ur
            JOIN FETCH ur.tenant t
            JOIN FETCH ur.role r
            WHERE ur.user.id = :userId
              AND ur.tenant IS NOT NULL
              AND ur.active = true
              AND ur.deleted = false
              AND t.deleted = false
              AND t.status = 'ACTIVE'
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<UserRole> findActiveTenantMembershipsByUserId(@Param("userId") Long userId);

    /**
     * Returns whether a given user has an active role in a specific tenant.
     */
    @Query("""
            SELECT COUNT(ur) > 0 FROM UserRole ur
            JOIN ur.role r
            WHERE ur.user.id = :userId
              AND ur.tenant.id = :tenantId
              AND r.roleName = :roleName
              AND ur.active = true
              AND ur.deleted = false
              AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveRoleByUserIdAndTenantIdAndRoleName(@Param("userId") Long userId,
                                                           @Param("tenantId") Long tenantId,
                                                           @Param("roleName") RoleType roleName);

    /**
     * Legacy check for active role by user ID and role name.
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
     */
    @Query("""
            SELECT DISTINCT ur.user.id FROM UserRole ur
            JOIN ur.role r
            WHERE ur.tenant.id = :tenantId
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

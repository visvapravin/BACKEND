package com.forumx.platform.invitation.repository;

import java.util.Optional;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.platform.invitation.entity.TenantAdminInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantAdminInvitationRepository extends JpaRepository<TenantAdminInvitation, Long> {

    Optional<TenantAdminInvitation> findByTokenHashAndDeletedFalse(String tokenHash);

    Optional<TenantAdminInvitation> findByTenant_IdAndEmailAndStatusAndDeletedFalse(
            Long tenantId, String email, InvitationStatus status);

    Page<TenantAdminInvitation> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    boolean existsByTenant_IdAndEmailAndStatusAndDeletedFalse(
            Long tenantId, String email, InvitationStatus status);

    /**
     * Bulk-revokes all PENDING tenant admin invitations for a tenant.
     * Called during tenant deactivation so outstanding invitation tokens cannot be accepted.
     *
     * @param tenantId the tenant whose pending admin invitations to revoke
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TenantAdminInvitation i
            SET i.status = com.forumx.auth.invitation.entity.InvitationStatus.REVOKED
            WHERE i.tenant.id = :tenantId
              AND i.status = com.forumx.auth.invitation.entity.InvitationStatus.PENDING
              AND i.deleted = false
            """)
    void revokeAllPendingByTenantId(@Param("tenantId") Long tenantId);
}

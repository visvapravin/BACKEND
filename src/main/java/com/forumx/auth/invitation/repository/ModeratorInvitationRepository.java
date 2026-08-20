package com.forumx.auth.invitation.repository;

import java.util.List;
import java.util.Optional;

import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.auth.invitation.entity.ModeratorInvitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModeratorInvitationRepository extends JpaRepository<ModeratorInvitation, Long> {

    Optional<ModeratorInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ModeratorInvitation i WHERE i.tokenHash = :tokenHash")
    Optional<ModeratorInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<ModeratorInvitation> findByTenant_IdAndEmailAndStatusAndDeletedFalse(Long tenantId, String email, InvitationStatus status);

    List<ModeratorInvitation> findByTenant_IdAndEmailAndStatusInAndDeletedFalse(Long tenantId, String email, List<InvitationStatus> statuses);

    List<ModeratorInvitation> findByTenant_IdAndDeletedFalse(Long tenantId);

    boolean existsByTenant_IdAndEmailAndStatusAndDeletedFalse(Long tenantId, String email, InvitationStatus status);

    // ── Phase C: paginated queries ──────────────────────────────────────

    /**
     * Paginated list of all non-deleted invitations for a tenant (all statuses).
     */
    Page<ModeratorInvitation> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    /**
     * Paginated list filtered by explicit status for a tenant.
     */
    Page<ModeratorInvitation> findByTenant_IdAndStatusAndDeletedFalse(Long tenantId,
                                                                       InvitationStatus status,
                                                                       Pageable pageable);

    /**
     * Finds a specific invitation by ID that belongs to the given tenant.
     * Used to enforce tenant isolation on single-invitation operations.
     */
    Optional<ModeratorInvitation> findByIdAndTenant_IdAndDeletedFalse(Long id, Long tenantId);

    /**
     * Bulk-revokes all PENDING moderator invitations for a tenant.
     * Called during tenant deactivation so outstanding tokens cannot be accepted.
     *
     * @param tenantId the tenant whose pending invitations to revoke
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            UPDATE ModeratorInvitation i
            SET i.status = com.forumx.auth.invitation.entity.InvitationStatus.REVOKED
            WHERE i.tenant.id = :tenantId
              AND i.status = com.forumx.auth.invitation.entity.InvitationStatus.PENDING
              AND i.deleted = false
            """)
    void revokeAllPendingByTenantId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}


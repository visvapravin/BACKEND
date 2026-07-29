package com.forumx.platform.invitation.repository;

import java.util.Optional;
import com.forumx.auth.invitation.entity.InvitationStatus;
import com.forumx.platform.invitation.entity.TenantAdminInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantAdminInvitationRepository extends JpaRepository<TenantAdminInvitation, Long> {

    Optional<TenantAdminInvitation> findByTokenHashAndDeletedFalse(String tokenHash);

    Optional<TenantAdminInvitation> findByTenant_IdAndEmailAndStatusAndDeletedFalse(
            Long tenantId, String email, InvitationStatus status);

    Page<TenantAdminInvitation> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    boolean existsByTenant_IdAndEmailAndStatusAndDeletedFalse(
            Long tenantId, String email, InvitationStatus status);
}

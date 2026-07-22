package com.forumx.notification.repository;

import java.time.Instant;
import java.util.Optional;

import com.forumx.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipient_IdAndTenant_IdAndDeletedFalse(Long recipientId, Long tenantId, Pageable pageable);

    Page<Notification> findByRecipient_IdAndTenant_IdAndReadFalseAndDeletedFalse(Long recipientId, Long tenantId, Pageable pageable);

    Optional<Notification> findByIdAndRecipient_IdAndTenant_IdAndDeletedFalse(Long id, Long recipientId, Long tenantId);

    long countByRecipient_IdAndTenant_IdAndReadFalseAndDeletedFalse(Long recipientId, Long tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.read = true, n.readAt = :readAt, n.updatedAt = :readAt
            WHERE n.recipient.id = :recipientId
              AND n.tenant.id = :tenantId
              AND n.read = false
              AND n.deleted = false
            """)
    int markAllAsRead(@Param("recipientId") Long recipientId,
                      @Param("tenantId") Long tenantId,
                      @Param("readAt") Instant readAt);
}

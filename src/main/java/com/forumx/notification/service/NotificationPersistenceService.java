package com.forumx.notification.service;

import java.time.Instant;
import java.util.Optional;
import com.forumx.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence-only service responsible for database interactions involving Notification entities.
 * Free of any messaging or external communication logic.
 */
public interface NotificationPersistenceService {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    Optional<Notification> findByIdAndRecipientAndTenant(Long id, Long recipientId, Long tenantId);

    Page<Notification> findByRecipientAndTenant(Long recipientId, Long tenantId, Pageable pageable);

    Page<Notification> findUnreadByRecipientAndTenant(Long recipientId, Long tenantId, Pageable pageable);

    long countUnreadByRecipientAndTenant(Long recipientId, Long tenantId);

    int markAllAsRead(Long recipientId, Long tenantId, Instant readAt);
}

package com.forumx.notification.service.impl;

import java.time.Instant;
import java.util.Optional;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.notification.service.NotificationPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-only implementation of NotificationPersistenceService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPersistenceServiceImpl implements NotificationPersistenceService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public Notification save(Notification notification) {
        log.debug("Persisting notification entity for recipient ID: {}", notification.getRecipient() != null ? notification.getRecipient().getId() : null);
        return notificationRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return notificationRepository.findById(id);
    }

    @Override
    public Optional<Notification> findByIdAndRecipientAndTenant(Long id, Long recipientId, Long tenantId) {
        return notificationRepository.findByIdAndRecipient_IdAndTenant_IdAndDeletedFalse(id, recipientId, tenantId);
    }

    @Override
    public Page<Notification> findByRecipientAndTenant(Long recipientId, Long tenantId, Pageable pageable) {
        return notificationRepository.findByRecipient_IdAndTenant_IdAndDeletedFalse(recipientId, tenantId, pageable);
    }

    @Override
    public Page<Notification> findUnreadByRecipientAndTenant(Long recipientId, Long tenantId, Pageable pageable) {
        return notificationRepository.findByRecipient_IdAndTenant_IdAndReadFalseAndDeletedFalse(recipientId, tenantId, pageable);
    }

    @Override
    public long countUnreadByRecipientAndTenant(Long recipientId, Long tenantId) {
        return notificationRepository.countByRecipient_IdAndTenant_IdAndReadFalseAndDeletedFalse(recipientId, tenantId);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long recipientId, Long tenantId, Instant readAt) {
        return notificationRepository.markAllAsRead(recipientId, tenantId, readAt);
    }
}

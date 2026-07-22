package com.forumx.notification.delivery.repository;

import com.forumx.notification.delivery.entity.DeliveryStatus;
import com.forumx.notification.delivery.entity.NotificationDeliveryAttempt;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttempt, Long> {

    List<NotificationDeliveryAttempt> findByNotification_IdOrderByCreatedAtDesc(Long notificationId);

    List<NotificationDeliveryAttempt> findByTenant_IdAndNotification_IdOrderByCreatedAtDesc(Long tenantId, Long notificationId);

    Page<NotificationDeliveryAttempt> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    Page<NotificationDeliveryAttempt> findByTenant_IdAndStatusAndDeletedFalse(Long tenantId, DeliveryStatus status, Pageable pageable);

    Page<NotificationDeliveryAttempt> findByStatusAndDeletedFalse(DeliveryStatus status, Pageable pageable);
}

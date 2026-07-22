package com.forumx.notification.repository;

import java.util.List;
import java.util.Optional;
import com.forumx.notification.entity.NotificationPreference;
import com.forumx.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByTenant_IdAndUser_IdAndNotificationTypeAndDeletedFalse(
            Long tenantId, Long userId, NotificationType notificationType);

    List<NotificationPreference> findAllByTenant_IdAndUser_IdAndDeletedFalse(Long tenantId, Long userId);
}

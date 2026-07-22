package com.forumx.notification.service;

import com.forumx.notification.api.NotificationCommand;
import com.forumx.notification.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Public boundary through which future modules interact with notifications. */
public interface NotificationApplicationService {
    void create(NotificationCommand command);
    com.forumx.notification.entity.Notification saveFromEvent(com.forumx.notification.dto.NotificationEvent event);
    Page<NotificationResponse> getNotifications(Pageable pageable);
    Page<NotificationResponse> getUnreadNotifications(Pageable pageable);
    NotificationResponse markAsRead(Long notificationId);
    void markAllAsRead();
    void deleteNotification(Long notificationId);
    long getUnreadCount();

    void notifyAnswerCreated(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long questionId);
    void notifyTicketCreated(Long tenantId, Long recipientId, Long actorId, Long ticketId);
    void notifyCustomerReply(Long tenantId, Long recipientId, Long actorId, Long ticketId);
    void notifySupportReply(Long tenantId, Long recipientId, Long actorId, Long ticketId);
    void notifyChatMessageReceived(com.forumx.notification.api.ChatNotificationCommand command);
    void queueEmail(com.forumx.notification.email.dto.EmailNotificationCommand command);

    void notifyQuestionCommentCreated(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long questionId);
    void notifyAnswerCommentCreated(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long answerId);
    void notifyQuestionUpvoted(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long questionId);
    void notifyAnswerUpvoted(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long answerId);
    void notifyCommentUpvoted(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long commentId);

    void notifyContentReported(Long tenantId, Long reporterId, String reporterUsername, Long targetId, com.forumx.notification.entity.ReferenceType targetType, Long authorId);
}

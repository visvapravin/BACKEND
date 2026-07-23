package com.forumx.notification.service.impl;

import java.time.Instant;
import java.util.Map;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.api.NotificationCommand;
import com.forumx.notification.dto.response.NotificationResponse;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;
import com.forumx.notification.mapper.NotificationMapper;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.notification.event.NotificationCreatedEvent;
import com.forumx.notification.event.NotificationCreatedSpringEvent;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.forumx.notification.service.NotificationPersistenceService;
import com.forumx.notification.dispatcher.NotificationDispatcher;
import com.forumx.notification.dispatcher.NotificationDeliveryRequest;
import com.forumx.notification.mapper.NotificationRealtimeMapper;
import com.forumx.websocket.gateway.RealtimeGateway;

/** Internal implementation of the Notification application boundary. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationApplicationService {

    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationPersistenceService notificationPersistenceService;
    private final NotificationDispatcher notificationDispatcher;
    private final RealtimeGateway realtimeGateway;
    private final NotificationRealtimeMapper realtimeMapper;

    @Override
    @Transactional
    public void create(NotificationCommand command) {
        if (command == null || command.tenantId() == null || command.recipientId() == null
                || command.notificationType() == null || command.referenceType() == null
                || command.title() == null || command.title().isBlank()
                || command.message() == null || command.message().isBlank()) {
            throw new IllegalArgumentException("A complete notification command is required");
        }

        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(command.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + command.tenantId()));
        User recipient = loadTenantUser(command.recipientId(), command.tenantId(), "Recipient");
        User actor = command.actorId() == null ? null : loadTenantUser(command.actorId(), command.tenantId(), "Actor");

        Notification notification = Notification.builder()
                .tenant(tenant)
                .recipient(recipient)
                .actor(actor)
                .notificationType(command.notificationType())
                .title(command.title())
                .message(command.message())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .read(false)
                .build();
        Notification savedNotification = notificationRepository.save(notification);
        log.info("Notification created. tenantId={}, recipientId={}, type={}",
                command.tenantId(), command.recipientId(), command.notificationType());

        NotificationCreatedEvent event = new NotificationCreatedEvent(
                savedNotification.getId(),
                recipient.getId(),
                recipient.getUsername(),
                tenant.getId(),
                savedNotification.getNotificationType().name(),
                savedNotification.getTitle(),
                savedNotification.getMessage(),
                actor != null ? actor.getId() : null,
                savedNotification.getReferenceType().name(),
                savedNotification.getReferenceId(),
                savedNotification.getCreatedAt() != null ? savedNotification.getCreatedAt() : Instant.now()
        );
        applicationEventPublisher.publishEvent(new NotificationCreatedSpringEvent(event));
    }

    @Override
    @Transactional
    public Notification saveFromEvent(com.forumx.notification.dto.NotificationEvent event) {
        if (event == null || event.tenantId() == null || event.userId() == null) {
            log.warn("Skipping notification persistence for invalid event: {}", event);
            return null;
        }

        NotificationType type;
        try {
            type = NotificationType.valueOf(event.type());
        } catch (Exception e) {
            type = NotificationType.EMAIL_VERIFICATION;
        }

        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(event.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + event.tenantId()));

        User recipient = userRepository.findByIdAndDeletedFalse(event.userId())
                .or(() -> userRepository.findByTenantIdAndEmailAndDeletedFalse(event.tenantId(), event.email()))
                .orElse(null);

        if (recipient == null) {
            log.warn("Skipping notification persistence as user could not be found by ID {} or email {}", event.userId(), event.email());
            return null;
        }

        Notification notification = Notification.builder()
                .tenant(tenant)
                .recipient(recipient)
                .actor(null)
                .notificationType(type)
                .title(event.subject() != null ? event.subject() : "Notification")
                .message(event.body() != null ? event.body() : "")
                .referenceType(ReferenceType.USER)
                .referenceId(event.userId())
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Persisted Notification from event for recipientId={}, type={}", recipient.getId(), type);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Pageable pageable) {
        CurrentRecipient recipient = resolveCurrentRecipient();
        return notificationRepository
                .findByRecipient_IdAndTenant_IdAndDeletedFalse(recipient.userId(), recipient.tenantId(), pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUnreadNotifications(Pageable pageable) {
        CurrentRecipient recipient = resolveCurrentRecipient();
        return notificationRepository
                .findByRecipient_IdAndTenant_IdAndReadFalseAndDeletedFalse(recipient.userId(), recipient.tenantId(), pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        CurrentRecipient recipient = resolveCurrentRecipient();
        Notification notification = notificationRepository
                .findByIdAndRecipient_IdAndTenant_IdAndDeletedFalse(notificationId, recipient.userId(), recipient.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Notification not found with ID: " + notificationId));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        CurrentRecipient recipient = resolveCurrentRecipient();
        notificationRepository.markAllAsRead(recipient.userId(), recipient.tenantId(), Instant.now());
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        CurrentRecipient recipient = resolveCurrentRecipient();
        Notification notification = notificationRepository
                .findByIdAndRecipient_IdAndTenant_IdAndDeletedFalse(notificationId, recipient.userId(), recipient.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Notification not found with ID: " + notificationId));

        notification.setDeleted(true);
        notification.setDeletedAt(Instant.now());
        notificationRepository.save(notification);
        log.info("Notification soft deleted. notificationId={}, recipientId={}", notificationId, recipient.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        CurrentRecipient recipient = resolveCurrentRecipient();
        return notificationRepository.countByRecipient_IdAndTenant_IdAndReadFalseAndDeletedFalse(
                recipient.userId(), recipient.tenantId());
    }

    @Override
    @Transactional
    public void notifyAnswerCreated(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long questionId) {
        create(new NotificationCommand(
                tenantId,
                recipientId,
                actorId,
                NotificationType.ANSWER_CREATED,
                "New Answer",
                actorUsername + " answered your question.",
                ReferenceType.QUESTION,
                questionId
        ));
    }

    @Override
    @Transactional
    public void notifyTicketCreated(Long tenantId, Long recipientId, Long actorId, Long ticketId) {
        create(new NotificationCommand(
                tenantId,
                recipientId,
                actorId,
                NotificationType.TICKET_CREATED,
                "New Support Ticket",
                "A new support ticket requires attention.",
                ReferenceType.TICKET,
                ticketId
        ));
    }

    @Override
    @Transactional
    public void notifyCustomerReply(Long tenantId, Long recipientId, Long actorId, Long ticketId) {
        create(new NotificationCommand(
                tenantId,
                recipientId,
                actorId,
                NotificationType.TICKET_MESSAGE,
                "Customer Replied",
                "Customer replied to a support ticket.",
                ReferenceType.TICKET,
                ticketId
        ));
    }

    @Override
    @Transactional
    public void notifySupportReply(Long tenantId, Long recipientId, Long actorId, Long ticketId) {
        create(new NotificationCommand(
                tenantId,
                recipientId,
                actorId,
                NotificationType.TICKET_MESSAGE,
                "Support Replied",
                "Support replied to your ticket.",
                ReferenceType.TICKET,
                ticketId
        ));
    }

    @Override
    @Transactional
    public void notifyChatMessageReceived(com.forumx.notification.api.ChatNotificationCommand command) {
        create(new NotificationCommand(
                command.tenantId(),
                command.recipientId(),
                command.actorId(),
                NotificationType.TICKET_MESSAGE,
                "New Chat Message",
                command.actorUsername() + ": " + command.messagePreview(),
                ReferenceType.TICKET,
                command.ticketId()
        ));

        User recipient = userRepository.findByIdAndDeletedFalse(command.recipientId())
                .orElseThrow(() -> new EntityNotFoundException("Recipient user not found with ID: " + command.recipientId()));

        Map<String, String> model = Map.of(
                "ticketId", String.valueOf(command.ticketId()),
                "senderName", command.actorUsername(),
                "messagePreview", command.messagePreview()
        );

        queueEmail(new com.forumx.notification.email.dto.EmailNotificationCommand(
                command.tenantId(),
                recipient.getEmail(),
                recipient.getUsername(),
                com.forumx.notification.email.EmailTemplateType.CHAT_MESSAGE,
                model
        ));
    }

    @Override
    @Transactional
    public void queueEmail(com.forumx.notification.email.dto.EmailNotificationCommand command) {
        log.info("Queueing email notification for recipient={}", command.recipientEmail());
        com.forumx.notification.email.event.EmailSendRequestedEvent event = new com.forumx.notification.email.event.EmailSendRequestedEvent(
                java.util.UUID.randomUUID(),
                command.tenantId(),
                command.recipientEmail(),
                command.recipientUsername(),
                command.templateType(),
                command.templateModel(),
                java.time.Instant.now()
        );
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    @Transactional
    public void notifyQuestionCommentCreated(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long questionId) {
        create(new NotificationCommand(
                tenantId,
                recipientId,
                actorId,
                NotificationType.QUESTION_COMMENTED,
                "New Comment",
                actorUsername + " commented on your question.",
                ReferenceType.QUESTION,
                questionId
        ));
    }

    @Override
    @Transactional
    public void notifyAnswerCommentCreated(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long answerId) {
        create(new NotificationCommand(
                tenantId,
                recipientId,
                actorId,
                NotificationType.QUESTION_COMMENTED,
                "New Comment",
                actorUsername + " commented on your answer.",
                ReferenceType.ANSWER,
                answerId
        ));
    }

    @Override public void notifyQuestionUpvoted(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long questionId) {
        create(new NotificationCommand(tenantId, recipientId, actorId, NotificationType.QUESTION_UPVOTED,
                "New Upvote", actorUsername + " upvoted your question.", ReferenceType.QUESTION, questionId));
    }
    @Override public void notifyAnswerUpvoted(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long answerId) {
        create(new NotificationCommand(tenantId, recipientId, actorId, NotificationType.ANSWER_UPVOTED,
                "New Upvote", actorUsername + " upvoted your answer.", ReferenceType.ANSWER, answerId));
    }
    @Override public void notifyCommentUpvoted(Long tenantId, Long recipientId, Long actorId, String actorUsername, Long commentId) {
        create(new NotificationCommand(tenantId, recipientId, actorId, NotificationType.COMMENT_UPVOTED,
                "New Upvote", actorUsername + " upvoted your comment.", ReferenceType.COMMENT, commentId));
    }

    @Override
    @Transactional
    public void notifyContentReported(Long tenantId, Long reporterId, String reporterUsername, Long targetId, ReferenceType targetType, Long authorId) {
        java.util.List<User> moderators = userRepository.findUsersByTenantIdAndRoles(
                tenantId,
                java.util.List.of(com.forumx.auth.enums.RoleType.MODERATOR, com.forumx.auth.enums.RoleType.ADMIN, com.forumx.auth.enums.RoleType.SUPER_ADMIN)
        );

        for (User moderator : moderators) {
            if (!moderator.getId().equals(reporterId) && !moderator.getId().equals(authorId)) {
                create(new NotificationCommand(
                        tenantId,
                        moderator.getId(),
                        reporterId,
                        NotificationType.CONTENT_REPORTED,
                        "Content Reported",
                        "A " + targetType.name().toLowerCase() + " was reported by " + reporterUsername + ".",
                        targetType,
                        targetId
                ));
            }
        }
    }

    private CurrentRecipient resolveCurrentRecipient() {
        Long tenantId = tenantResolver.resolveTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("No tenant context is available");
        }
        CustomUserDetails userDetails = authenticationFacade.getCurrentUserDetails();
        if (userDetails == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        if (!tenantId.equals(userDetails.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        loadTenantUser(userDetails.getUserId(), tenantId, "Current user");
        return new CurrentRecipient(userDetails.getUserId(), tenantId);
    }

    private User loadTenantUser(Long userId, Long tenantId, String description) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new EntityNotFoundException(description + " not found with ID: " + userId));
        if (user.getTenant() == null || !tenantId.equals(user.getTenant().getId())) {
            throw new AccessDeniedException(description + " does not belong to the notification tenant");
        }
        return user;
    }

    private record CurrentRecipient(Long userId, Long tenantId) {
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void processNotificationCreated(NotificationCreatedEvent event) {
        if (event == null) {
            log.warn("Cannot process null NotificationCreatedEvent");
            return;
        }

        log.info("Orchestrating NotificationCreatedEvent in NotificationApplicationService. notificationId={}, recipientUsername={}",
                event.notificationId(), event.recipientUsername());

        // 1. Retrieve or verify persisted entity via NotificationPersistenceService
        Notification notification = null;
        if (event.notificationId() != null) {
            notification = notificationPersistenceService.findById(event.notificationId()).orElse(null);
        }

        // 2. Multi-channel delivery dispatching
        if (notification != null && notificationDispatcher != null) {
            try {
                notificationDispatcher.dispatch(new NotificationDeliveryRequest(notification, null));
            } catch (Exception e) {
                log.error("Failed dispatching notification delivery for notificationId={}: {}", event.notificationId(), e.getMessage(), e);
            }
        }

        // 3. Realtime WebSocket Push via RealtimeGateway
        if (event.recipientUsername() != null && realtimeGateway != null && realtimeMapper != null) {
            try {
                var realtimeEvent = realtimeMapper.toRealtimeEvent(event);
                realtimeGateway.sendToUser(event.recipientUsername(), "/queue/notifications", realtimeEvent);
                log.info("Pushed real-time WebSocket notification to recipientUsername={}", event.recipientUsername());
            } catch (Exception e) {
                log.error("Failed executing WebSocket push for recipientUsername={}: {}", event.recipientUsername(), e.getMessage(), e);
            }
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void processNotificationEvent(com.forumx.notification.dto.NotificationEvent event) {
        if (event == null) {
            log.warn("Cannot process null NotificationEvent");
            return;
        }

        log.info("Orchestrating NotificationEvent in NotificationApplicationService for email={}, type={}", event.email(), event.type());
        Notification notification = saveFromEvent(event);

        if (notification != null && notificationDispatcher != null) {
            try {
                notificationDispatcher.dispatch(new NotificationDeliveryRequest(notification, event));
            } catch (Exception e) {
                log.error("Failed dispatching delivery for event {}: {}", event.notificationId(), e.getMessage(), e);
            }
        }
    }
}

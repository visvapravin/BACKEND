package com.forumx.support.chat.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.presence.service.PresenceService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.MessageDeliveryStatus;
import com.forumx.support.chat.entity.MessageType;
import com.forumx.support.chat.event.durable.ChatMessageDeletedEvent;
import com.forumx.support.chat.event.durable.ChatMessageReadEvent;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.repository.ChatMessageRepository;
import com.forumx.support.chat.service.ChatPermissionService;
import com.forumx.support.chat.service.ChatService;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionService chatSessionService;
    private final ChatPermissionService chatPermissionService;
    private final ChatMessageRepository chatMessageRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TenantResolver tenantResolver;
    private final AuthenticationFacade authenticationFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final PresenceService presenceService;
    private final NotificationApplicationService notificationApplicationService;

    @Override
    @Transactional
    public ChatMessage sendMessage(Long ticketId, SendMessageRequest request) {
        CurrentUser current = resolveCurrentUser();
        Ticket ticket = ticketRepository.findByIdAndTenant_IdAndDeletedFalse(ticketId, current.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found with ID: " + ticketId));

        ChatSession session = chatSessionService.getOrCreateSession(ticket, current.tenantId());
        chatPermissionService.assertCanSend(session, current.userId());

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sender(current.user())
                .messageType(MessageType.TEXT)
                .content(request.getContent())
                .deliveryStatus(MessageDeliveryStatus.SENT)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);

        // Publish event to Spring's event publisher
        ChatMessageSentEvent event = new ChatMessageSentEvent(
                UUID.randomUUID(),
                current.tenantId(),
                session.getId(),
                ticket.getId(),
                current.userId(),
                current.user().getUsername(),
                savedMessage.getId(),
                savedMessage.getContent(),
                savedMessage.getMessageType().name(),
                Instant.now()
        );
        eventPublisher.publishEvent(event);

        // Trigger notification if recipient is offline
        Long recipientId = null;
        if (session.getCustomer() != null && session.getCustomer().getId().equals(current.userId())) {
            if (session.getModerator() != null) {
                recipientId = session.getModerator().getId();
            }
        } else if (session.getCustomer() != null) {
            recipientId = session.getCustomer().getId();
        }

        if (recipientId != null && !presenceService.isUserOnlineInTenant(current.tenantId(), recipientId)) {
            String preview = savedMessage.getContent();
            if (preview != null && preview.length() > 100) {
                preview = preview.substring(0, 100) + "...";
            }
            com.forumx.notification.api.ChatNotificationCommand notifCmd = new com.forumx.notification.api.ChatNotificationCommand(
                    current.tenantId(),
                    recipientId,
                    current.userId(),
                    current.user().getUsername(),
                    session.getId(),
                    ticket.getId(),
                    preview
            );
            notificationApplicationService.notifyChatMessageReceived(notifCmd);
        }

        return savedMessage;
    }

    @Override
    @Transactional
    public void deleteMessage(Long messageId) {
        CurrentUser current = resolveCurrentUser();
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found with ID: " + messageId));

        ChatSession session = message.getSession();
        if (!session.getTenant().getId().equals(current.tenantId())) {
            throw new AccessDeniedException("Message does not belong to the current tenant");
        }

        chatPermissionService.assertCanDelete(session, current.userId(), message);

        message.setContent("This message was deleted.");
        message.setDeleted(true);
        message.setDeletedAt(Instant.now());
        chatMessageRepository.save(message);

        ChatMessageDeletedEvent event = new ChatMessageDeletedEvent(
                UUID.randomUUID(),
                current.tenantId(),
                session.getId(),
                session.getTicket().getId(),
                current.userId(),
                message.getId(),
                Instant.now()
        );
        eventPublisher.publishEvent(event);
    }

    @Override
    @Transactional
    public void markRead(Long ticketId) {
        CurrentUser current = resolveCurrentUser();
        ChatSession session = chatSessionService.getSessionByTicketId(ticketId, current.tenantId());
        chatPermissionService.assertCanRead(session, current.userId());

        List<ChatMessage> unread = chatMessageRepository.findBySession_IdAndSender_IdNotAndDeliveryStatusNotAndDeletedFalse(
                session.getId(), current.userId(), MessageDeliveryStatus.READ
        );

        if (!unread.isEmpty()) {
            for (ChatMessage msg : unread) {
                msg.setDeliveryStatus(MessageDeliveryStatus.READ);
            }
            chatMessageRepository.saveAll(unread);

            Instant maxTimestamp = unread.stream()
                    .map(m -> m.getCreatedAt() != null ? m.getCreatedAt() : Instant.now())
                    .max(Instant::compareTo)
                    .orElse(Instant.now());

            ChatMessageReadEvent event = new ChatMessageReadEvent(
                UUID.randomUUID(),
                current.tenantId(),
                session.getId(),
                ticketId,
                current.userId(),
                maxTimestamp,
                Instant.now()
            );
            eventPublisher.publishEvent(event);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessage> getMessages(Long ticketId, Long beforeMessageId, Pageable pageable) {
        CurrentUser current = resolveCurrentUser();
        ChatSession session = chatSessionService.getSessionByTicketId(ticketId, current.tenantId());
        chatPermissionService.assertCanRead(session, current.userId());

        // Customers view full ticket conversation history
        boolean isCustomer = session.getCustomer() != null && session.getCustomer().getId().equals(current.userId());
        if (isCustomer) {
            if (beforeMessageId == null) {
                return chatMessageRepository.findMessagesFirstPage(session.getId(), pageable);
            } else {
                return chatMessageRepository.findMessagesBefore(session.getId(), beforeMessageId, pageable);
            }
        }

        // Support staff retrieve messages created strictly within their active participation windows
        if (beforeMessageId == null) {
            return chatMessageRepository.findAuthorizedMessagesFirstPage(session.getId(), current.userId(), pageable);
        } else {
            return chatMessageRepository.findAuthorizedMessagesBefore(session.getId(), current.userId(), beforeMessageId, pageable);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getLatestMessages(Long ticketId, Long beforeMessageId, int size) {
        CurrentUser current = resolveCurrentUser();
        ChatSession session = chatSessionService.getSessionByTicketId(ticketId, current.tenantId());
        chatPermissionService.assertCanRead(session, current.userId());
        int boundedSize = Math.min(Math.max(size, 1), 101);
        Pageable limit = org.springframework.data.domain.PageRequest.of(0, boundedSize);
        ChatMessage cursor = null;
        if (beforeMessageId != null) {
            cursor = chatMessageRepository.findById(beforeMessageId)
                    .orElseThrow(() -> new EntityNotFoundException("Cursor message not found"));
            if (!cursor.getSession().getId().equals(session.getId())) {
                throw new AccessDeniedException("Cursor message does not belong to this chat session");
            }
        }
        boolean isCustomer = session.getCustomer() != null && session.getCustomer().getId().equals(current.userId());
        if (isCustomer) {
            return cursor == null ? chatMessageRepository.findLatestMessages(session.getId(), limit)
                    : chatMessageRepository.findLatestMessagesBefore(session.getId(), cursor.getCreatedAt(), cursor.getId(), limit);
        }
        return cursor == null ? chatMessageRepository.findAuthorizedLatestMessages(session.getId(), current.userId(), limit)
                : chatMessageRepository.findAuthorizedLatestMessagesBefore(session.getId(), current.userId(), cursor.getCreatedAt(), cursor.getId(), limit);
    }

    @Override
    @Transactional
    public ChatSession getSession(Long ticketId) {
        CurrentUser current = resolveCurrentUser();
        Ticket ticket = ticketRepository.findByIdAndTenant_IdAndDeletedFalse(ticketId, current.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found with ID: " + ticketId));
        ChatSession session = chatSessionService.getOrCreateSession(ticket, current.tenantId());
        chatPermissionService.assertCanRead(session, current.userId());
        return session;
    }

    private CurrentUser resolveCurrentUser() {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (tenantId == null || details == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        if (!tenantId.equals(details.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
        // Authoritative tenant membership check via UserRole — do NOT use user.getTenant()
        List<UserRole> activeRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(user.getId(), tenantId);
        if (activeRoles.isEmpty()) {
            throw new AccessDeniedException("User does not have active membership in the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user, details);
    }

    private record CurrentUser(Long userId, Long tenantId, User user, CustomUserDetails details) {
    }
}

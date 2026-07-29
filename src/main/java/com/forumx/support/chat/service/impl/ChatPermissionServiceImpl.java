package com.forumx.support.chat.service.impl;

import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.ChatPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatPermissionServiceImpl implements ChatPermissionService {

    private final AuthenticationFacade authenticationFacade;
    private final SupportSessionParticipantRepository participantRepository;

    @Override
    public void assertCanJoin(ChatSession session, Long userId) {
        if (!canAccessSession(session, userId)) {
            throw new AccessDeniedException("User is not authorized to join this chat session");
        }
    }

    @Override
    public void assertCanSend(ChatSession session, Long userId) {
        if (session.getStatus() == ChatSessionStatus.CLOSED || session.getStatus() == ChatSessionStatus.RESOLVED) {
            throw new IllegalStateException("Cannot send message. Chat session is " + session.getStatus().name().toLowerCase() + ".");
        }
        boolean isCustomer = session.getCustomer() != null && session.getCustomer().getId().equals(userId);
        boolean isActiveParticipant = participantRepository.existsBySession_Ticket_IdAndUser_IdAndIsActiveTrue(session.getTicket().getId(), userId);

        if (!isCustomer && !isActiveParticipant) {
            throw new AccessDeniedException("User is not an active participant in this chat session. Please join the room first.");
        }
    }

    @Override
    public void assertCanRead(ChatSession session, Long userId) {
        if (!canAccessSession(session, userId)) {
            throw new AccessDeniedException("User is not authorized to view this chat session");
        }
    }

    @Override
    public void assertCanDelete(ChatSession session, Long userId, ChatMessage message) {
        if (!message.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("User is not the sender of this message");
        }
        if (message.isDeleted()) {
            throw new IllegalStateException("Message is already deleted.");
        }
        if (session.getStatus() == ChatSessionStatus.CLOSED || session.getStatus() == ChatSessionStatus.RESOLVED) {
            throw new IllegalStateException("Cannot delete message. Chat session is " + session.getStatus().name().toLowerCase() + ".");
        }
    }

    private boolean canAccessSession(ChatSession session, Long userId) {
        if (session.getCustomer() != null && session.getCustomer().getId().equals(userId)) {
            return true;
        }
        if (session.getModerator() != null && session.getModerator().getId().equals(userId)) {
            return true;
        }
        if (participantRepository.existsBySession_Ticket_IdAndUser_Id(session.getTicket().getId(), userId)) {
            return true;
        }
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        return details != null && isElevated(details);
    }

    private boolean isElevated(CustomUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_TENANT_ADMIN")
                        || authority.equals("ROLE_PLATFORM_ADMIN")
                        || authority.equals("ROLE_MODERATOR")
                        || authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN"));
    }
}

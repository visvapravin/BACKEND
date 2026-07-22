package com.forumx.support.chat.service.impl;

import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.service.ChatPermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ChatPermissionServiceImpl implements ChatPermissionService {

    @Override
    public void assertCanJoin(ChatSession session, Long userId) {
        if (!isParticipant(session, userId)) {
            throw new AccessDeniedException("User is not a participant in this chat session");
        }
    }

    @Override
    public void assertCanSend(ChatSession session, Long userId) {
        if (!isParticipant(session, userId)) {
            throw new AccessDeniedException("User is not a participant in this chat session");
        }
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new IllegalStateException("Cannot send message. Chat session is closed.");
        }
    }

    @Override
    public void assertCanRead(ChatSession session, Long userId) {
        if (!isParticipant(session, userId)) {
            throw new AccessDeniedException("User is not a participant in this chat session");
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
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new IllegalStateException("Cannot delete message. Chat session is closed.");
        }
    }

    private boolean isParticipant(ChatSession session, Long userId) {
        return session.getCustomer().getId().equals(userId) ||
               session.getModerator().getId().equals(userId);
    }
}

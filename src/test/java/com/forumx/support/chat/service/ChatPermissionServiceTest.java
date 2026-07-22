package com.forumx.support.chat.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forumx.auth.entity.User;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.service.impl.ChatPermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

public class ChatPermissionServiceTest {

    private ChatPermissionServiceImpl permissionService;
    private ChatSession session;
    private User customer;
    private User moderator;
    private User outsider;

    @BeforeEach
    public void setUp() {
        permissionService = new ChatPermissionServiceImpl();

        customer = User.builder().id(1L).username("customer").build();
        moderator = User.builder().id(2L).username("moderator").build();
        outsider = User.builder().id(3L).username("outsider").build();

        session = ChatSession.builder()
                .id(10L)
                .customer(customer)
                .moderator(moderator)
                .status(ChatSessionStatus.ACTIVE)
                .build();
    }

    @Test
    public void testAssertCanJoin() {
        assertDoesNotThrow(() -> permissionService.assertCanJoin(session, customer.getId()));
        assertDoesNotThrow(() -> permissionService.assertCanJoin(session, moderator.getId()));
        assertThrows(AccessDeniedException.class, () -> permissionService.assertCanJoin(session, outsider.getId()));
    }

    @Test
    public void testAssertCanSend() {
        assertDoesNotThrow(() -> permissionService.assertCanSend(session, customer.getId()));
        
        // CLOSED session should reject sending messages
        session.setStatus(ChatSessionStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> permissionService.assertCanSend(session, customer.getId()));
        
        // Outsider should be denied
        assertThrows(AccessDeniedException.class, () -> permissionService.assertCanSend(session, outsider.getId()));
    }

    @Test
    public void testAssertCanRead() {
        assertDoesNotThrow(() -> permissionService.assertCanRead(session, customer.getId()));
        
        // CLOSED session still allows reading messages
        session.setStatus(ChatSessionStatus.CLOSED);
        assertDoesNotThrow(() -> permissionService.assertCanRead(session, customer.getId()));
        
        // Outsider denied
        assertThrows(AccessDeniedException.class, () -> permissionService.assertCanRead(session, outsider.getId()));
    }

    @Test
    public void testAssertCanDelete() {
        ChatMessage msg = ChatMessage.builder()
                .id(100L)
                .sender(customer)
                .session(session)
                .deleted(false)
                .build();

        // Sender can delete in active session
        assertDoesNotThrow(() -> permissionService.assertCanDelete(session, customer.getId(), msg));

        // Moderator (non-sender) cannot delete customer's message
        assertThrows(AccessDeniedException.class, () -> permissionService.assertCanDelete(session, moderator.getId(), msg));

        // Cannot delete already deleted message
        msg.setDeleted(true);
        assertThrows(IllegalStateException.class, () -> permissionService.assertCanDelete(session, customer.getId(), msg));

        // Restore message deleted status
        msg.setDeleted(false);

        // Cannot delete in closed session
        session.setStatus(ChatSessionStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> permissionService.assertCanDelete(session, customer.getId(), msg));
    }
}

package com.forumx.support.chat.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.forumx.auth.entity.User;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.impl.ChatPermissionServiceImpl;
import com.forumx.support.ticket.entity.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class ChatPermissionServiceTest {

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private SupportSessionParticipantRepository participantRepository;

    private ChatPermissionServiceImpl permissionService;
    private ChatSession session;
    private User customer;
    private User moderator;
    private User outsider;

    @BeforeEach
    public void setUp() {
        permissionService = new ChatPermissionServiceImpl(authenticationFacade, participantRepository);

        customer = User.builder().id(1L).username("customer").build();
        moderator = User.builder().id(2L).username("moderator").build();
        outsider = User.builder().id(3L).username("outsider").build();

        Ticket ticket = Ticket.builder().id(100L).build();
        session = ChatSession.builder()
                .id(10L)
                .ticket(ticket)
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

        // RESOLVED session should also reject sending messages
        session.setStatus(ChatSessionStatus.RESOLVED);
        assertThrows(IllegalStateException.class, () -> permissionService.assertCanSend(session, customer.getId()));
        
        // Reset to ACTIVE and verify outsider is denied
        session.setStatus(ChatSessionStatus.ACTIVE);
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

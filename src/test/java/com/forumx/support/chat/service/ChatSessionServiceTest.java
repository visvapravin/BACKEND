package com.forumx.support.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.service.impl.ChatSessionServiceImpl;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ChatSessionServiceTest {

    @Mock private ChatSessionRepository chatSessionRepository;
    @InjectMocks private ChatSessionServiceImpl chatSessionService;

    private Tenant tenant;
    private User customer;
    private User moderator;
    private Ticket ticket;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).build();
        customer = User.builder().id(10L).tenant(tenant).build();
        moderator = User.builder().id(20L).tenant(tenant).build();
        ticket = Ticket.builder()
                .id(100L)
                .tenant(tenant)
                .creator(customer)
                .assignedTo(moderator)
                .build();
    }

    @Test
    public void testGetOrCreateSessionRetrievesExisting() {
        ChatSession existing = ChatSession.builder().id(200L).ticket(ticket).tenant(tenant).build();
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.of(existing));

        ChatSession result = chatSessionService.getOrCreateSession(ticket, 1L);

        assertEquals(existing, result);
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    public void testGetOrCreateSessionCreatesNewSuccessfully() {
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.empty());

        ChatSession expectedSaved = ChatSession.builder()
                .id(200L)
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .moderator(moderator)
                .status(ChatSessionStatus.ACTIVE)
                .build();

        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(expectedSaved);

        ChatSession result = chatSessionService.getOrCreateSession(ticket, 1L);

        assertNotNull(result);
        assertEquals(200L, result.getId());
        
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(sessionCaptor.capture());
        ChatSession captured = sessionCaptor.getValue();
        assertEquals(ticket, captured.getTicket());
        assertEquals(customer, captured.getCustomer());
        assertEquals(moderator, captured.getModerator());
        assertEquals(ChatSessionStatus.ACTIVE, captured.getStatus());
    }

    @Test
    public void testGetOrCreateSessionThrowsIfUnassigned() {
        ticket.setAssignedTo(null); // unassigned

        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> chatSessionService.getOrCreateSession(ticket, 1L));
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    public void testGetSessionByTicketIdFound() {
        ChatSession session = ChatSession.builder().id(200L).build();
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.of(session));

        ChatSession result = chatSessionService.getSessionByTicketId(100L, 1L);
        assertEquals(session, result);
    }

    @Test
    public void testGetSessionByTicketIdNotFoundThrows() {
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> chatSessionService.getSessionByTicketId(100L, 1L));
    }

    @Test
    public void testCloseSessionByTicketIdSuccessfully() {
        ChatSession session = ChatSession.builder()
                .id(200L)
                .status(ChatSessionStatus.ACTIVE)
                .build();
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.of(session));

        chatSessionService.closeSessionByTicketId(100L, 1L);

        assertEquals(ChatSessionStatus.CLOSED, session.getStatus());
        verify(chatSessionRepository).save(session);
    }

    @Test
    public void testCloseSessionByTicketIdNotFoundNoOp() {
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.empty());

        chatSessionService.closeSessionByTicketId(100L, 1L);

        verify(chatSessionRepository, never()).save(any());
    }
}

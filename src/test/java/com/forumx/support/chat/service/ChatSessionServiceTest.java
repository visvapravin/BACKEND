package com.forumx.support.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.presence.service.PresenceService;
import com.forumx.support.chat.dto.response.ParticipantResponse;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.event.ephemeral.ChatParticipantJoinedEvent;
import com.forumx.support.chat.event.ephemeral.ChatParticipantLeftEvent;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.impl.ChatSessionServiceImpl;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class ChatSessionServiceTest {

    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private SupportSessionParticipantRepository participantRepository;
    @Mock private PresenceService presenceService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ChatSessionServiceImpl chatSessionService;

    private Tenant tenant;
    private User customer;
    private User moderator;
    private Ticket ticket;
    private ChatSession chatSession;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).build();
        customer = User.builder().id(10L).tenant(tenant).build();
        moderator = User.builder().id(20L).tenant(tenant).build();
        ticket = Ticket.builder()
                .id(100L)
                .tenant(tenant)
                .creator(customer)
                .build();
        chatSession = ChatSession.builder()
                .id(200L)
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .status(ChatSessionStatus.ACTIVE)
                .build();
    }

    @Test
    public void testGetOrCreateSessionRetrievesExisting() {
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.of(chatSession));

        ChatSession result = chatSessionService.getOrCreateSession(ticket, 1L);

        assertEquals(chatSession, result);
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    public void testGetOrCreateSessionCreatesNewAndRegistersCustomerParticipant() {
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.empty());
        
        ChatSession waitingSession = ChatSession.builder()
                .id(200L)
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .status(ChatSessionStatus.WAITING)
                .build();
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(waitingSession);

        ChatSession result = chatSessionService.getOrCreateSession(ticket, 1L);

        assertNotNull(result);
        assertEquals(200L, result.getId());
        assertEquals(ChatSessionStatus.WAITING, result.getStatus());
        verify(participantRepository).save(any(SupportSessionParticipant.class));
    }

    @Test
    public void testJoinRoomFailsIfModeratorOffline() {
        when(presenceService.isOnline(20L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> 
                chatSessionService.joinRoom(100L, moderator, ParticipantRole.MODERATOR));

        verify(participantRepository, never()).save(any());
    }

    @Test
    public void testJoinRoomSuccessWhenModeratorOnline() {
        when(presenceService.isOnline(20L)).thenReturn(true);
        when(chatSessionRepository.findByTicket_IdAndDeletedFalse(100L)).thenReturn(Optional.of(chatSession));
        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(100L, 20L)).thenReturn(Optional.empty());
        when(participantRepository.findTopBySession_Ticket_IdAndUser_IdOrderByJoinedAtDesc(100L, 20L)).thenReturn(Optional.empty());
        
        SupportSessionParticipant savedParticipant = SupportSessionParticipant.builder()
                .id(1L)
                .session(chatSession)
                .tenant(tenant)
                .user(moderator)
                .role(ParticipantRole.MODERATOR)
                .isActive(true)
                .build();

        when(participantRepository.save(any())).thenReturn(savedParticipant);

        SupportSessionParticipant result = chatSessionService.joinRoom(100L, moderator, ParticipantRole.MODERATOR);

        assertNotNull(result);
        assertEquals(ParticipantRole.MODERATOR, result.getRole());
        verify(eventPublisher).publishEvent(any(ChatParticipantJoinedEvent.class));
    }

    @Test
    public void testLeaveRoomSuccess() {
        SupportSessionParticipant activeParticipant = SupportSessionParticipant.builder()
                .id(1L)
                .session(chatSession)
                .tenant(tenant)
                .user(moderator)
                .role(ParticipantRole.MODERATOR)
                .isActive(true)
                .build();

        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(100L, 20L))
                .thenReturn(Optional.of(activeParticipant));
        when(participantRepository.save(any())).thenReturn(activeParticipant);

        SupportSessionParticipant result = chatSessionService.leaveRoom(100L, moderator);

        assertFalse(result.isActive());
        assertNotNull(result.getLeftAt());
        verify(eventPublisher).publishEvent(any(ChatParticipantLeftEvent.class));
    }

    @Test
    public void testGetParticipantsReturnsList() {
        SupportSessionParticipant p = SupportSessionParticipant.builder()
                .id(1L)
                .session(chatSession)
                .tenant(tenant)
                .user(moderator)
                .role(ParticipantRole.MODERATOR)
                .isActive(true)
                .build();

        when(participantRepository.findBySession_Ticket_IdAndTenant_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(List.of(p));
        when(presenceService.isOnline(20L)).thenReturn(true);

        List<ParticipantResponse> result = chatSessionService.getParticipants(100L, 1L);

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getUserId());
        assertTrue(result.get(0).isOnline());
    }
}

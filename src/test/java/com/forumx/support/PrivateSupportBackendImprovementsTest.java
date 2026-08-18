package com.forumx.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.presence.service.PresenceService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.dto.request.TypingPayload;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.event.ephemeral.ChatParticipantJoinedEvent;
import com.forumx.support.chat.event.ephemeral.TypingStartedEvent;
import com.forumx.support.chat.publisher.ChatEventPublisher;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.impl.ChatPermissionServiceImpl;
import com.forumx.support.chat.service.impl.ChatSessionServiceImpl;
import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.support.ticket.mapper.TicketMapper;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.impl.TicketServiceImpl;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class PrivateSupportBackendImprovementsTest {

    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private SupportSessionParticipantRepository participantRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PresenceService presenceService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationApplicationService notificationApplicationService;
    @Mock private RealtimeGateway realtimeGateway;
    @Mock private AuthenticationFacade authenticationFacade;
    @Mock private TenantResolver tenantResolver;
    @Mock private TicketMapper ticketMapper;

    private ChatSessionServiceImpl chatSessionService;
    private TicketServiceImpl ticketService;
    private ChatPermissionServiceImpl permissionService;
    private ChatEventPublisher chatEventPublisher;

    private Tenant tenant;
    private User customer;
    private User moderator;
    private Ticket ticket;
    private ChatSession chatSession;
    private CustomUserDetails moderatorDetails;

    @BeforeEach
    public void setUp() {
        chatSessionService = new ChatSessionServiceImpl(
                chatSessionRepository,
                participantRepository,
                ticketRepository,
                presenceService,
                eventPublisher
        );
        ticketService = new TicketServiceImpl(
                ticketRepository, userRepository, tenantRepository, ticketMapper,
                authenticationFacade, tenantResolver, notificationApplicationService, eventPublisher,
                chatSessionService, chatSessionRepository, presenceService, new com.forumx.support.ticket.policy.TicketStatusTransitionPolicy());

        permissionService = new ChatPermissionServiceImpl(authenticationFacade, participantRepository);

        chatEventPublisher = new ChatEventPublisher(null, realtimeGateway);

        tenant = Tenant.builder().id(1L).slug("default").build();
        customer = User.builder().id(10L).username("alice").tenant(tenant).build();
        moderator = User.builder().id(20L).username("bob").tenant(tenant).build();

        ticket = Ticket.builder()
                .id(100L)
                .tenant(tenant)
                .creator(customer)
                .status(TicketStatus.OPEN)
                .build();

        chatSession = ChatSession.builder()
                .id(200L)
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .status(ChatSessionStatus.WAITING)
                .build();

        moderatorDetails = new CustomUserDetails(
                User.builder().id(20L).username("bob").tenant(tenant).build()) {
            @Override
            public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MODERATOR"));
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBG2_RichTypingEventPayloadOutbound() {
        TypingStartedEvent event = new TypingStartedEvent(200L, 1L, 20L, "bob", "Bob");

        chatEventPublisher.onTypingStarted(event);

        ArgumentCaptor<RealtimeEvent<TypingPayload>> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway).sendToTopic(eq("/topic/tenants/1/chat/200/typing"), captor.capture());

        RealtimeEvent<TypingPayload> sentEvent = captor.getValue();
        assertEquals("TYPING_STARTED", sentEvent.getType());
        assertEquals("START", sentEvent.getPayload().getAction());
        assertEquals(200L, sentEvent.getPayload().getSessionId());
        assertEquals(20L, sentEvent.getPayload().getUserId());
        assertEquals("bob", sentEvent.getPayload().getUsername());
        assertEquals("Bob", sentEvent.getPayload().getDisplayName());
    }

    @Test
    public void testBG11_ChatSessionInitialStatusIsWaiting() {
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> i.getArgument(0));

        ChatSession created = chatSessionService.getOrCreateSession(ticket, 1L);

        assertEquals(ChatSessionStatus.WAITING, created.getStatus());
    }

    @Test
    public void testBG11_ModeratorJoinTransitionsWaitingToActive() {
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(chatSession));
        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(100L, 20L)).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SupportSessionParticipant participant = chatSessionService.joinRoom(100L, moderator, ParticipantRole.MODERATOR);

        assertNotNull(participant);
        assertEquals(ChatSessionStatus.ACTIVE, chatSession.getStatus());
        verify(eventPublisher).publishEvent(any(ChatParticipantJoinedEvent.class));
    }

    @Test
    public void testBG9_AssignTicketAutoJoinsModeratorAndActivatesChatSession() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(moderator));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(chatSession));
        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(100L, 20L)).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AssignTicketRequest request = new AssignTicketRequest();
        request.setAssignedToUserId(20L);

        ticketService.assignTicket(100L, request);

        assertEquals(moderator, ticket.getAssignedTo());
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals(ChatSessionStatus.ACTIVE, chatSession.getStatus());
        assertEquals(moderator, chatSession.getModerator());
        verify(eventPublisher).publishEvent(any(ChatParticipantJoinedEvent.class));
    }

    @Test
    public void testBG9_AutoJoinIsIdempotentWhenAlreadyActive() {
        SupportSessionParticipant activeParticipant = SupportSessionParticipant.builder()
                .id(1L)
                .session(chatSession)
                .tenant(tenant)
                .user(moderator)
                .role(ParticipantRole.MODERATOR)
                .joinedAt(Instant.now().minusSeconds(300))
                .isActive(true)
                .build();

        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(chatSession));
        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(100L, 20L))
                .thenReturn(Optional.of(activeParticipant));

        SupportSessionParticipant result = chatSessionService.joinRoom(100L, moderator, ParticipantRole.MODERATOR);

        assertEquals(activeParticipant, result);
        verify(participantRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(ChatParticipantJoinedEvent.class));
    }

    @Test
    public void testBG9_RejoiningModeratorEstablishesNewJoinBoundary() {
        Instant oldJoin = Instant.now().minusSeconds(1000);

        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(chatSession));
        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(100L, 20L)).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SupportSessionParticipant reactivated = chatSessionService.joinRoom(100L, moderator, ParticipantRole.MODERATOR);

        assertTrue(reactivated.isActive());
        assertNull(reactivated.getLeftAt());
        assertNotEquals(oldJoin, reactivated.getJoinedAt());
        verify(eventPublisher).publishEvent(any(ChatParticipantJoinedEvent.class));
    }

    @Test
    public void testBG11_ResolvedAndClosedSessionsRejectNewMessages() {
        chatSession.setStatus(ChatSessionStatus.RESOLVED);
        assertThrows(IllegalStateException.class, () -> permissionService.assertCanSend(chatSession, customer.getId()));

        chatSession.setStatus(ChatSessionStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> permissionService.assertCanSend(chatSession, customer.getId()));
    }

    @Test
    public void testBG4_ElevatedUserHistoryConsistency_NonParticipantRejected() {
        // Moderator who never joined should be rejected by permissionService.assertCanRead
        when(participantRepository.existsBySession_Ticket_IdAndUser_Id(100L, 99L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> permissionService.assertCanRead(chatSession, 99L));
    }

    @Test
    public void testBG7_TicketStateMachine_InvalidTransitionRejected() {
        com.forumx.support.ticket.policy.TicketStatusTransitionPolicy policy = new com.forumx.support.ticket.policy.TicketStatusTransitionPolicy();

        // CLOSED -> IN_PROGRESS is invalid
        assertThrows(IllegalStateException.class, () -> policy.validateTransition(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS));

        // Valid transitions
        assertDoesNotThrow(() -> policy.validateTransition(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));
        assertDoesNotThrow(() -> policy.validateTransition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
        assertDoesNotThrow(() -> policy.validateTransition(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        assertDoesNotThrow(() -> policy.validateTransition(TicketStatus.CLOSED, TicketStatus.REOPENED));
    }

    @Test
    public void testBG8_DashboardSummaryAggregation() {
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(userRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(moderator));

        when(ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(1L, TicketStatus.OPEN)).thenReturn(4L);
        when(ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(1L, TicketStatus.IN_PROGRESS)).thenReturn(7L);
        when(ticketRepository.countByTenant_IdAndStatusInAndDeletedFalse(eq(1L), anyCollection())).thenReturn(25L);
        when(ticketRepository.countByTenant_IdAndResolvedAtGreaterThanEqualAndResolvedAtLessThanAndDeletedFalse(eq(1L), any(), any())).thenReturn(6L);


        com.forumx.support.ticket.dto.response.SupportDashboardSummary summary = ticketService.getDashboardSummary();

        assertEquals(4L, summary.waiting());
        assertEquals(7L, summary.open());
        assertEquals(25L, summary.closed());
        assertEquals(6L, summary.resolvedToday());
    }
}



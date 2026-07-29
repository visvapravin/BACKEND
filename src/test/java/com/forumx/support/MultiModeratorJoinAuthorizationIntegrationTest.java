package com.forumx.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.presence.service.PresenceService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.impl.ChatPermissionServiceImpl;
import com.forumx.support.chat.service.impl.ChatSessionServiceImpl;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
public class MultiModeratorJoinAuthorizationIntegrationTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private SupportSessionParticipantRepository participantRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private PresenceService presenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuthenticationFacade authenticationFacade;

    private ChatSessionServiceImpl chatSessionService;
    private ChatPermissionServiceImpl chatPermissionService;

    private Tenant tenant;
    private User customer;
    private User moderatorA;
    private User moderatorB;
    private Ticket ticket;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        chatSessionService = new ChatSessionServiceImpl(
                chatSessionRepository,
                participantRepository,
                ticketRepository,
                presenceService,
                eventPublisher
        );
        chatPermissionService = new ChatPermissionServiceImpl(
                authenticationFacade,
                participantRepository
        );

        tenant = Tenant.builder().id(1L).name("Default Tenant").slug("default").build();
        Role modRole = Role.builder().id(2L).roleName(RoleType.MODERATOR).build();

        customer = User.builder().id(10L).username("customer_a").tenant(tenant).build();
        moderatorA = User.builder().id(20L).username("moderator_a").tenant(tenant).build();
        UserRole userRoleA = UserRole.builder().user(moderatorA).role(modRole).build();
        moderatorA.setUserRoles(Set.of(userRoleA));

        moderatorB = User.builder().id(30L).username("moderator_b").tenant(tenant).build();
        UserRole userRoleB = UserRole.builder().user(moderatorB).role(modRole).build();
        moderatorB.setUserRoles(Set.of(userRoleB));

        ticket = Ticket.builder()
                .id(128L)
                .tenant(tenant)
                .creator(customer)
                .status(TicketStatus.OPEN)
                .build();

        session = ChatSession.builder()
                .id(100L)
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .status(ChatSessionStatus.WAITING)
                .build();
    }

    @Test
    void testModeratorBJoinsActiveRoomWithoutBeingLeadAssigned() {
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setAssignedTo(moderatorA);
        session.setModerator(moderatorA);
        session.setStatus(ChatSessionStatus.ACTIVE);

        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(128L, 1L))
                .thenReturn(Optional.of(ticket));
        when(chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(128L, 1L))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(128L, 30L))
                .thenReturn(Optional.empty());
        when(participantRepository.save(any(SupportSessionParticipant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SupportSessionParticipant participantB = chatSessionService.joinRoom(128L, moderatorB, ParticipantRole.MODERATOR, true);

        assertNotNull(participantB);
        assertEquals(moderatorB.getId(), participantB.getUser().getId());
        assertTrue(participantB.isActive());
        assertEquals(ParticipantRole.MODERATOR, participantB.getRole());
    }

    @Test
    void testElevatedModeratorCanReadUnassignedSessionMetadata() {
        CustomUserDetails details = new CustomUserDetails(moderatorA);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);

        assertDoesNotThrow(() -> chatPermissionService.assertCanRead(session, 20L));
    }

    @Test
    void testUnauthenticatedOrCrossTenantUserDeniedJoin() {
        User crossTenantUser = User.builder().id(99L).username("hacker").tenant(null).build();
        assertThrows(AccessDeniedException.class, () -> chatSessionService.joinRoom(128L, crossTenantUser, ParticipantRole.MODERATOR, true));
    }
}

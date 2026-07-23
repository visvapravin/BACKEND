package com.forumx.support.queue.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.support.queue.event.SupportTicketQueuedEvent;
import com.forumx.support.queue.listener.SupportQueueEventSubscriber;
import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.entity.TicketPriority;

import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = "forumx.messaging.enabled=true")
public class SupportQueueRealtimeIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private com.forumx.support.ticket.repository.TicketRepository ticketRepository;

    @Autowired
    private com.forumx.auth.repository.UserRoleRepository userRoleRepository;


    @Autowired
    private com.forumx.auth.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private com.forumx.auth.verification.repository.VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private com.forumx.auth.repository.UserProfileRepository userProfileRepository;

    @Autowired private com.forumx.bookmark.repository.BookmarkRepository bookmarkRepository;
    @Autowired private com.forumx.question.repository.QuestionRepository questionRepository;

    @Autowired
    private com.forumx.answer.repository.AnswerRepository answerRepository;

    @Autowired
    private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;

    @Autowired
    private com.forumx.comment.repository.CommentRepository commentRepository;

    @Autowired
    private com.forumx.moderation.repository.ModerationReportRepository reportRepository;

    @Autowired
    private com.forumx.notification.repository.NotificationRepository notificationRepository;

    @Autowired
    private SupportQueueEventSubscriber subscriber;


    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private RealtimeGateway realtimeGateway;

    @MockBean
    private com.forumx.redis.gateway.RedisGateway redisGateway;

    @MockBean
    private com.forumx.security.facade.AuthenticationFacade authenticationFacade;


    @MockBean
    private com.forumx.tenant.resolver.TenantResolver tenantResolver;

    @Autowired
    private com.forumx.auth.repository.RoleRepository roleRepository;

    private Tenant tenant;
    private User creator;
    private User moderator;
    private com.forumx.security.model.CustomUserDetails creatorDetails;
    private com.forumx.security.model.CustomUserDetails moderatorDetails;


    @BeforeEach
    public void setUp() {
        ticketMessageRepository.deleteAllInBatch();
        reportRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        answerRepository.deleteAllInBatch();
        bookmarkRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        verificationTokenRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRoleRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        tenant = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("default"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Default")
                        .slug("default")
                        .build()));

        creator = userRepository.save(User.builder()
                .username("ticket_creator")
                .email("creator@test.com")
                .tenant(tenant)
                .enabled(true)
                .build());

        moderator = userRepository.save(User.builder()
                .username("mod_agent")
                .email("mod@test.com")
                .tenant(tenant)
                .enabled(true)
                .build());

        com.forumx.auth.entity.Role userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(com.forumx.auth.entity.Role.builder()
                        .roleName(RoleType.USER)
                        .description("User role")
                        .active(true)
                        .build()));

        com.forumx.auth.entity.Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR)
                .orElseGet(() -> roleRepository.save(com.forumx.auth.entity.Role.builder()
                        .roleName(RoleType.MODERATOR)
                        .description("Moderator role")
                        .active(true)
                        .build()));

        userRoleRepository.save(com.forumx.auth.entity.UserRole.builder()
                .user(creator)
                .role(userRole)
                .active(true)
                .build());

        userRoleRepository.save(com.forumx.auth.entity.UserRole.builder()
                .user(moderator)
                .role(modRole)
                .active(true)
                .build());


        creator = userRepository.findByIdWithFullProfile(creator.getId()).orElseThrow();
        moderator = userRepository.findByIdWithFullProfile(moderator.getId()).orElseThrow();

        creatorDetails = new com.forumx.security.model.CustomUserDetails(creator);
        moderatorDetails = new com.forumx.security.model.CustomUserDetails(moderator);
    }


    @Test
    public void testTicketCreationAndSubscriberBroadcast() {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(creatorDetails);

        CreateTicketRequest request = new CreateTicketRequest();
        request.setSubject("Live Queue Issue");
        request.setDescription("Cannot connect to server");
        request.setPriority(TicketPriority.HIGH);

        var ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(request));
        assertNotNull(ticketResponse);

        // Simulate RabbitMQ delivery to SupportQueueEventSubscriber
        SupportTicketQueuedEvent event = new SupportTicketQueuedEvent(
                ticketResponse.getId(),
                tenant.getId(),
                creator.getId(),
                creator.getUsername(),
                "Live Queue Issue",
                "HIGH",
                "OPEN",
                java.time.Instant.now()
        );

        EventEnvelope<SupportTicketQueuedEvent> envelope = EventEnvelope.of(
                "SUPPORT_TICKET_QUEUED", tenant.getId(), "support-service", event
        );

        subscriber.onSupportQueueEvent(envelope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<SupportTicketQueuedEvent>> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway).sendToTopic(eq("/topic/support/queue"), captor.capture());

        RealtimeEvent<SupportTicketQueuedEvent> broadcastEvent = captor.getValue();
        assertEquals("SUPPORT_TICKET_QUEUED", broadcastEvent.getType());
        assertEquals(event, broadcastEvent.getPayload());
    }

    @Test
    public void testTicketClaimingAndSubscriberBroadcast() {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);

        // First create ticket as creator
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(creatorDetails);
        CreateTicketRequest request = new CreateTicketRequest();
        request.setSubject("Claim Test");
        request.setDescription("Needs claim");
        var ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(request));

        // Claim ticket as moderator
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        AssignTicketRequest assignRequest = new AssignTicketRequest();
        assignRequest.setAssignedToUserId(moderator.getId());

        var claimedResponse = transactionTemplate.execute(status -> ticketService.assignTicket(ticketResponse.getId(), assignRequest));
        assertNotNull(claimedResponse);

        // Simulate RabbitMQ delivery for claim event
        com.forumx.support.queue.event.SupportTicketClaimedEvent claimedEvent = new com.forumx.support.queue.event.SupportTicketClaimedEvent(
                claimedResponse.getId(),
                tenant.getId(),
                moderator.getId(),
                moderator.getUsername(),
                java.time.Instant.now()
        );

        EventEnvelope<com.forumx.support.queue.event.SupportTicketClaimedEvent> envelope = EventEnvelope.of(
                "SUPPORT_TICKET_CLAIMED", tenant.getId(), "support-service", claimedEvent
        );

        subscriber.onSupportQueueEvent(envelope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<com.forumx.support.queue.event.SupportTicketClaimedEvent>> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeGateway, times(1)).sendToTopic(eq("/topic/support/queue"), captor.capture());

        RealtimeEvent<com.forumx.support.queue.event.SupportTicketClaimedEvent> broadcastEvent = captor.getValue();
        assertEquals("SUPPORT_TICKET_CLAIMED", broadcastEvent.getType());
        assertEquals(claimedEvent, broadcastEvent.getPayload());
    }
}

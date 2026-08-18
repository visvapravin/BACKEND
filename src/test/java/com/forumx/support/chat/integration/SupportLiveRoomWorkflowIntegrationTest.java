package com.forumx.support.chat.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.presence.service.PresenceService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.dto.response.ParticipantResponse;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.repository.ChatMessageRepository;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.ChatService;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = {"spring.jpa.hibernate.ddl-auto=update"})
public class SupportLiveRoomWorkflowIntegrationTest {

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ChatService chatService;
    @Autowired private ChatSessionService chatSessionService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private com.forumx.auth.repository.RoleRepository roleRepository;
    @Autowired private com.forumx.auth.repository.UserRoleRepository userRoleRepository;
    @Autowired private ChatSessionRepository chatSessionRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private SupportSessionParticipantRepository participantRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private PresenceService presenceService;
    @MockBean private AuthenticationFacade authenticationFacade;
    @MockBean private TenantResolver tenantResolver;
    @MockBean private com.forumx.websocket.gateway.RealtimeGateway realtimeGateway;
    @MockBean private com.forumx.redis.gateway.RedisGateway redisGateway;

    @Autowired private com.forumx.auth.repository.UserProfileRepository userProfileRepository;
    @Autowired private com.forumx.auth.verification.repository.VerificationTokenRepository verificationTokenRepository;
    @Autowired private com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private com.forumx.auth.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired private com.forumx.bookmark.repository.BookmarkRepository bookmarkRepository;
    @Autowired private com.forumx.question.repository.QuestionRepository questionRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;
    @Autowired private com.forumx.comment.repository.CommentRepository commentRepository;
    @Autowired private com.forumx.moderation.repository.ModerationReportRepository reportRepository;
    @Autowired private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;

    private Tenant tenant;
    private User customer;
    private User moderatorBob;
    private User moderatorAlice;
    private User moderatorCharlie;
    private CustomUserDetails customerDetails;
    private CustomUserDetails bobDetails;
    private CustomUserDetails aliceDetails;
    private CustomUserDetails charlieDetails;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        jdbcTemplate.execute("ALTER TABLE chat_sessions ALTER COLUMN moderator_id DROP NOT NULL");
        transactionTemplate.execute(status -> {
            ticketMessageRepository.deleteAllInBatch();
            reportRepository.deleteAllInBatch();
            commentRepository.deleteAllInBatch();
            notificationRepository.deleteAllInBatch();
            chatMessageRepository.deleteAllInBatch();
            participantRepository.deleteAllInBatch();
            chatSessionRepository.deleteAllInBatch();
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

            customer = userRepository.save(User.builder().username("customer_user").email("customer@test.com").tenant(tenant).enabled(true).build());
            moderatorBob = userRepository.save(User.builder().username("bob_mod").email("bob@test.com").tenant(tenant).enabled(true).build());
            moderatorAlice = userRepository.save(User.builder().username("alice_mod").email("alice@test.com").tenant(tenant).enabled(true).build());
            moderatorCharlie = userRepository.save(User.builder().username("charlie_mod").email("charlie@test.com").tenant(tenant).enabled(true).build());

            com.forumx.auth.entity.Role userRole = roleRepository.findByRoleName(RoleType.USER)
                    .orElseGet(() -> roleRepository.save(com.forumx.auth.entity.Role.builder().roleName(RoleType.USER).description("User role").active(true).build()));

            com.forumx.auth.entity.Role modRole = roleRepository.findByRoleName(RoleType.MODERATOR)
                    .orElseGet(() -> roleRepository.save(com.forumx.auth.entity.Role.builder().roleName(RoleType.MODERATOR).description("Moderator role").active(true).build()));

            userRoleRepository.save(com.forumx.auth.entity.UserRole.builder().user(customer).role(userRole).active(true).build());
            userRoleRepository.save(com.forumx.auth.entity.UserRole.builder().user(moderatorBob).role(modRole).active(true).build());
            userRoleRepository.save(com.forumx.auth.entity.UserRole.builder().user(moderatorAlice).role(modRole).active(true).build());
            userRoleRepository.save(com.forumx.auth.entity.UserRole.builder().user(moderatorCharlie).role(modRole).active(true).build());

            return null;
        });

        customer = userRepository.findByIdWithFullProfile(customer.getId()).orElseThrow();
        moderatorBob = userRepository.findByIdWithFullProfile(moderatorBob.getId()).orElseThrow();
        moderatorAlice = userRepository.findByIdWithFullProfile(moderatorAlice.getId()).orElseThrow();
        moderatorCharlie = userRepository.findByIdWithFullProfile(moderatorCharlie.getId()).orElseThrow();

        customerDetails = new CustomUserDetails(customer);
        bobDetails = new CustomUserDetails(moderatorBob);
        aliceDetails = new CustomUserDetails(moderatorAlice);
        charlieDetails = new CustomUserDetails(moderatorCharlie);
    }

    private void mockUser(User user, CustomUserDetails details) {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
    }

    @Test
    public void testScenario1_CustomerCreatesTicket_BobJoins_BobSeesNoPreviousMessages_BobChats() {
        // Customer creates ticket & sends pre-join messages
        mockUser(customer, customerDetails);
        CreateTicketRequest createRequest = new CreateTicketRequest();
        createRequest.setSubject("Payment Failed");
        createRequest.setDescription("Card declined at checkout");
        createRequest.setPriority(TicketPriority.HIGH);

        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(createRequest));
        assertNotNull(ticketResponse);

        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Hello")));
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Payment failed")));

        // Moderator Bob is online and joins at 10:03
        when(presenceService.isOnline(moderatorBob.getId())).thenReturn(true);
        mockUser(moderatorBob, bobDetails);

        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorBob, ParticipantRole.MODERATOR));

        // Bob requests messages -> Bob sees no previous messages (0 messages prior to join)
        Page<ChatMessage> bobMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertTrue(bobMessages.isEmpty(), "Bob must not see messages created prior to his join time");

        // Customer sends "Still there?"
        mockUser(customer, customerDetails);
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Still there?")));

        // Bob replies "Hi, I can help you."
        mockUser(moderatorBob, bobDetails);
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Hi, I can help you.")));

        // Bob requests messages -> Bob sees only "Still there?" and "Hi, I can help you."
        bobMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(2, bobMessages.getTotalElements());
        assertEquals("Still there?", bobMessages.getContent().get(0).getContent());
        assertEquals("Hi, I can help you.", bobMessages.getContent().get(1).getContent());
    }

    @Test
    public void testScenario2_AliceJoinsLater_CannotSeePreviousMessages() {
        mockUser(customer, customerDetails);
        CreateTicketRequest createRequest = new CreateTicketRequest();
        createRequest.setSubject("Multi-Agent Test");
        createRequest.setDescription("Testing join history cutoff");
        createRequest.setPriority(TicketPriority.MEDIUM);

        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(createRequest));

        // Bob joins first
        when(presenceService.isOnline(moderatorBob.getId())).thenReturn(true);
        mockUser(moderatorBob, bobDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorBob, ParticipantRole.MODERATOR));

        // Bob chats
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Bob message 1")));

        // Alice joins later
        when(presenceService.isOnline(moderatorAlice.getId())).thenReturn(true);
        mockUser(moderatorAlice, aliceDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorAlice, ParticipantRole.MODERATOR));

        // Alice fetches messages -> Sees 0 messages
        Page<ChatMessage> aliceMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertTrue(aliceMessages.isEmpty(), "Alice must not receive messages sent before her joinedAt");

        // Message sent after Alice joined
        mockUser(customer, customerDetails);
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Message after Alice joined")));

        // Alice fetches messages -> Sees only the message posted after her join
        mockUser(moderatorAlice, aliceDetails);
        aliceMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(1, aliceMessages.getTotalElements());
        assertEquals("Message after Alice joined", aliceMessages.getContent().get(0).getContent());
    }

    @Test
    public void testScenario3_BobLeaves_AliceContinues() {
        mockUser(customer, customerDetails);
        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(new CreateTicketRequest() {{
            setSubject("Leave Test");
            setDescription("Testing leave lifecycle");
            setPriority(TicketPriority.LOW);
        }}));

        when(presenceService.isOnline(moderatorBob.getId())).thenReturn(true);
        when(presenceService.isOnline(moderatorAlice.getId())).thenReturn(true);

        // Bob and Alice join
        mockUser(moderatorBob, bobDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorBob, ParticipantRole.MODERATOR));

        mockUser(moderatorAlice, aliceDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorAlice, ParticipantRole.MODERATOR));

        // Customer sends message M1 while Bob is present
        mockUser(customer, customerDetails);
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Msg 1 while Bob in room")));

        // Bob leaves
        mockUser(moderatorBob, bobDetails);
        SupportSessionParticipant leftBob = transactionTemplate.execute(status -> chatSessionService.leaveRoom(ticketResponse.getId(), moderatorBob));
        assertFalse(leftBob.isActive());
        assertNotNull(leftBob.getLeftAt());

        // Active participants list only contains Alice and Customer
        List<ParticipantResponse> activeParticipants = chatSessionService.getParticipants(ticketResponse.getId(), tenant.getId());
        assertEquals(2, activeParticipants.size());
        assertTrue(activeParticipants.stream().anyMatch(p -> p.getUserId().equals(moderatorAlice.getId())));
        assertFalse(activeParticipants.stream().anyMatch(p -> p.getUserId().equals(moderatorBob.getId())));

        // Customer sends message M2 AFTER Bob left
        mockUser(customer, customerDetails);
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Msg 2 after Bob left")));

        // Bob fetches messages -> Bob only sees Msg 1 (sent before leftAt), NOT Msg 2!
        mockUser(moderatorBob, bobDetails);
        Page<ChatMessage> bobMessagesAfterLeave = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(1, bobMessagesAfterLeave.getTotalElements());
        assertEquals("Msg 1 while Bob in room", bobMessagesAfterLeave.getContent().get(0).getContent());
    }

    @Test
    public void testScenario4_ThreeModeratorsJoin_IndependentVisibility() {
        mockUser(customer, customerDetails);
        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(new CreateTicketRequest() {{
            setSubject("3 Moderators Test");
            setDescription("Multiple moderators joinedAt isolation");
            setPriority(TicketPriority.HIGH);
        }}));

        when(presenceService.isOnline(moderatorBob.getId())).thenReturn(true);
        when(presenceService.isOnline(moderatorAlice.getId())).thenReturn(true);
        when(presenceService.isOnline(moderatorCharlie.getId())).thenReturn(true);

        // Bob joins -> Sends M1
        mockUser(moderatorBob, bobDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorBob, ParticipantRole.MODERATOR));
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Message 1 by Bob")));

        // Alice joins -> Sends M2
        mockUser(moderatorAlice, aliceDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorAlice, ParticipantRole.MODERATOR));
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Message 2 by Alice")));

        // Charlie joins -> Sends M3
        mockUser(moderatorCharlie, charlieDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorCharlie, ParticipantRole.MODERATOR));
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Message 3 by Charlie")));

        // Bob sees all 3 messages (M1, M2, M3)
        mockUser(moderatorBob, bobDetails);
        Page<ChatMessage> bobMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(3, bobMessages.getTotalElements());

        // Alice sees M2 and M3 (after Alice's joinedAt)
        mockUser(moderatorAlice, aliceDetails);
        Page<ChatMessage> aliceMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(2, aliceMessages.getTotalElements());

        // Charlie sees M3 (after Charlie's joinedAt)
        mockUser(moderatorCharlie, charlieDetails);
        Page<ChatMessage> charlieMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(1, charlieMessages.getTotalElements());
        assertEquals("Message 3 by Charlie", charlieMessages.getContent().get(0).getContent());
    }

    @Test
    public void testScenario5_CustomerReconnects_SeesCompleteHistory() {
        mockUser(customer, customerDetails);
        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(new CreateTicketRequest() {{
            setSubject("Customer Full History Test");
            setDescription("Customer reconnecting");
            setPriority(TicketPriority.HIGH);
        }}));

        // Customer message 1 & 2
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Customer Msg 1")));
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Customer Msg 2")));

        // Bob joins and replies
        when(presenceService.isOnline(moderatorBob.getId())).thenReturn(true);
        mockUser(moderatorBob, bobDetails);
        transactionTemplate.execute(status -> chatSessionService.joinRoom(ticketResponse.getId(), moderatorBob, ParticipantRole.MODERATOR));
        transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), new SendMessageRequest("Bob reply")));

        // Customer reconnects (fetches messages) -> Sees ALL 3 messages
        mockUser(customer, customerDetails);
        Page<ChatMessage> customerMessages = chatService.getMessages(ticketResponse.getId(), null, PageRequest.of(0, 10));
        assertEquals(3, customerMessages.getTotalElements());
        assertEquals("Customer Msg 1", customerMessages.getContent().get(0).getContent());
        assertEquals("Customer Msg 2", customerMessages.getContent().get(1).getContent());
        assertEquals("Bob reply", customerMessages.getContent().get(2).getContent());
    }
}

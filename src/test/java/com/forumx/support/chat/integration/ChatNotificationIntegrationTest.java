package com.forumx.support.chat.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.service.ChatService;
import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class)
public class ChatNotificationIntegrationTest {

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ChatService chatService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private com.forumx.auth.repository.RoleRepository roleRepository;
    @Autowired private com.forumx.auth.repository.UserRoleRepository userRoleRepository;
    @Autowired private com.forumx.support.chat.repository.ChatSessionRepository chatSessionRepository;
    @Autowired private com.forumx.support.chat.repository.ChatMessageRepository chatMessageRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private com.forumx.presence.service.PresenceService presenceService;
    @MockBean private com.forumx.security.facade.AuthenticationFacade authenticationFacade;
    @MockBean private com.forumx.tenant.resolver.TenantResolver tenantResolver;
    @MockBean private com.forumx.websocket.gateway.RealtimeGateway realtimeGateway;
    @MockBean private com.forumx.redis.gateway.RedisGateway redisGateway;

    @Autowired private com.forumx.auth.repository.UserProfileRepository userProfileRepository;
    @Autowired private com.forumx.auth.verification.repository.VerificationTokenRepository verificationTokenRepository;
    @Autowired private com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private com.forumx.auth.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired private com.forumx.bookmark.repository.BookmarkRepository bookmarkRepository;
    private com.forumx.question.repository.QuestionRepository questionRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;
    @Autowired private com.forumx.comment.repository.CommentRepository commentRepository;
    @Autowired private com.forumx.moderation.repository.ModerationReportRepository reportRepository;
    @Autowired private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;

    private Tenant tenant;
    private User customer;
    private User moderator;
    private com.forumx.security.model.CustomUserDetails customerDetails;
    private com.forumx.security.model.CustomUserDetails moderatorDetails;

    @BeforeEach
    public void setUp() {
        transactionTemplate.execute(status -> {
            ticketMessageRepository.deleteAllInBatch();
            reportRepository.deleteAllInBatch();
            commentRepository.deleteAllInBatch();
            notificationRepository.deleteAllInBatch();
            chatMessageRepository.deleteAllInBatch();
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

            customer = userRepository.save(User.builder()
                    .username("chat_creator")
                    .email("creator@chat.com")
                    .tenant(tenant)
                    .enabled(true)
                    .build());

            moderator = userRepository.save(User.builder()
                    .username("chat_mod_agent")
                    .email("mod@chat.com")
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
                    .user(customer)
                    .role(userRole)
                    .active(true)
                    .build());

            userRoleRepository.save(com.forumx.auth.entity.UserRole.builder()
                    .user(moderator)
                    .role(modRole)
                    .active(true)
                    .build());

            return null;
        });

        customer = userRepository.findByIdWithFullProfile(customer.getId()).orElseThrow();
        moderator = userRepository.findByIdWithFullProfile(moderator.getId()).orElseThrow();

        customerDetails = new com.forumx.security.model.CustomUserDetails(customer);
        moderatorDetails = new com.forumx.security.model.CustomUserDetails(moderator);
    }

    @Test
    public void testNotificationTriggeredOnMessageSentWhenRecipientOffline() {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);

        // 1. Create ticket
        CreateTicketRequest createRequest = new CreateTicketRequest();
        createRequest.setSubject("Offline chat notif test");
        createRequest.setDescription("Details here");
        createRequest.setPriority(TicketPriority.HIGH);

        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(createRequest));

        // 2. Assign ticket
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        AssignTicketRequest assignRequest = new AssignTicketRequest();
        assignRequest.setAssignedToUserId(moderator.getId());
        transactionTemplate.execute(status -> ticketService.assignTicket(ticketResponse.getId(), assignRequest));

        // 3. Mark recipient (moderator) as OFFLINE
        when(presenceService.isOnline(moderator.getId())).thenReturn(false);

        // 4. Send chat message
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        SendMessageRequest sendRequest = new SendMessageRequest("This should trigger a persistent notification!");
        
        ChatMessage savedMessage = transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), sendRequest));
        assertNotNull(savedMessage);

        // 5. Verify that a notification record was created in the database for the moderator
        long notificationCount = notificationRepository.count();
        assertTrue(notificationCount >= 1, "Expected at least 1 notification record in database");
    }
}

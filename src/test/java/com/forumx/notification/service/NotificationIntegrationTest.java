package com.forumx.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import com.forumx.ForumXApplication;
import com.forumx.answer.dto.request.CreateAnswerRequest;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.answer.service.AnswerService;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
import com.forumx.notification.entity.Notification;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.question.entity.Question;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.repository.TicketMessageRepository;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.TicketMessageService;
import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class)
public class NotificationIntegrationTest {

    @Autowired
    private AnswerService answerService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketMessageService ticketMessageService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired private com.forumx.bookmark.repository.BookmarkRepository bookmarkRepository;
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    @Autowired
    private com.forumx.comment.repository.CommentRepository commentRepository;

    @Autowired
    private com.forumx.moderation.repository.ModerationReportRepository reportRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private AuthenticationFacade authenticationFacade;

    @MockBean
    private TenantResolver tenantResolver;

    private Tenant tenant1;
    private Tenant tenant2;

    private User tenant1User;
    private User tenant1Answerer;
    private User tenant1Moderator;
    private User tenant1Admin;

    private User tenant2User;
    private User tenant2Moderator;

    private Question tenant1Question;

    @BeforeEach
    public void setUp() {
        // Clean up tables with strict foreign key constraints in correct dependent order
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

        // 1. Setup Tenants (lookup or save to prevent unique constraint failures on subsequent setups)
        tenant1 = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("default"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Tenant 1")
                        .slug("default")
                        .build()));

        tenant2 = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("tenant2"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Tenant 2")
                        .slug("tenant2")
                        .build()));

        // 2. Setup Roles
        Role userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).active(true).build()));
        Role moderatorRole = roleRepository.findByRoleName(RoleType.MODERATOR)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.MODERATOR).active(true).build()));
        Role adminRole = roleRepository.findByRoleName(RoleType.ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.ADMIN).active(true).build()));

        // 3. Setup Tenant 1 Users
        tenant1User = userRepository.save(User.builder()
                .username("t1_user")
                .email("t1_user@test.com")
                .tenant(tenant1)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(tenant1User).role(userRole).active(true).build());

        tenant1Answerer = userRepository.save(User.builder()
                .username("t1_answerer")
                .email("t1_answerer@test.com")
                .tenant(tenant1)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(tenant1Answerer).role(userRole).active(true).build());

        tenant1Moderator = userRepository.save(User.builder()
                .username("t1_moderator")
                .email("t1_mod@test.com")
                .tenant(tenant1)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(tenant1Moderator).role(moderatorRole).active(true).build());

        tenant1Admin = userRepository.save(User.builder()
                .username("t1_admin")
                .email("t1_admin@test.com")
                .tenant(tenant1)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(tenant1Admin).role(adminRole).active(true).build());

        // 4. Setup Tenant 2 Users
        tenant2User = userRepository.save(User.builder()
                .username("t2_user")
                .email("t2_user@test.com")
                .tenant(tenant2)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(tenant2User).role(userRole).active(true).build());

        tenant2Moderator = userRepository.save(User.builder()
                .username("t2_moderator")
                .email("t2_mod@test.com")
                .tenant(tenant2)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(tenant2Moderator).role(moderatorRole).active(true).build());

        // Refresh users from DB eagerly with full profile and roles to prevent LazyInitializationException
        tenant1User = userRepository.findByIdWithFullProfile(tenant1User.getId()).orElseThrow();
        tenant1Answerer = userRepository.findByIdWithFullProfile(tenant1Answerer.getId()).orElseThrow();
        tenant1Moderator = userRepository.findByIdWithFullProfile(tenant1Moderator.getId()).orElseThrow();
        tenant1Admin = userRepository.findByIdWithFullProfile(tenant1Admin.getId()).orElseThrow();
        tenant2User = userRepository.findByIdWithFullProfile(tenant2User.getId()).orElseThrow();
        tenant2Moderator = userRepository.findByIdWithFullProfile(tenant2Moderator.getId()).orElseThrow();

        // 5. Setup Tenant 1 Question
        tenant1Question = questionRepository.save(Question.builder()
                .title("T1 Question")
                .content("Content")
                .author(tenant1User)
                .tenant(tenant1)
                .build());
    }

    private void authenticateUser(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
        when(tenantResolver.resolveTenantId()).thenReturn(user.getTenant().getId());
    }

    @Test
    public void testAnswerNotification_Success() {
        authenticateUser(tenant1Answerer);

        CreateAnswerRequest request = new CreateAnswerRequest("Here is a helpful answer.");
        answerService.createAnswer(tenant1Question.getId(), request);

        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        
        Notification notification = notifications.get(0);
        assertEquals(tenant1User.getId(), notification.getRecipient().getId()); // Question creator is notified
        assertEquals(tenant1Answerer.getId(), notification.getActor().getId());
        assertEquals(NotificationType.ANSWER_CREATED, notification.getNotificationType());
        assertEquals("New Answer", notification.getTitle());
        assertEquals("t1_answerer answered your question.", notification.getMessage());
    }

    @Test
    public void testAnswerNotification_SelfReply_NoNotification() {
        authenticateUser(tenant1User); // Question owner replies to themselves

        CreateAnswerRequest request = new CreateAnswerRequest("Self reply content.");
        answerService.createAnswer(tenant1Question.getId(), request);

        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.isEmpty(), "Self replies should not trigger notifications.");
    }

    @Test
    public void testTicketNotification_DeduplicationAndFiltering() {
        authenticateUser(tenant1User);

        CreateTicketRequest request = new CreateTicketRequest("Urgent issue", "Description", TicketPriority.HIGH);
        ticketService.createTicket(request);

        List<Notification> notifications = notificationRepository.findAll();
        // There are 2 moderators in Tenant 1 (tenant1Moderator and tenant1Admin). Excludes tenant1User (creator).
        // Super admin role is not seeded for anyone, so they are not notified.
        assertEquals(2, notifications.size());

        Set<Long> recipientIds = Set.of(
                notifications.get(0).getRecipient().getId(),
                notifications.get(1).getRecipient().getId()
        );
        assertTrue(recipientIds.contains(tenant1Moderator.getId()));
        assertTrue(recipientIds.contains(tenant1Admin.getId()));
        assertFalse(recipientIds.contains(tenant1User.getId())); // Creator excluded
    }

    @Test
    public void testTicketMessageNotification_CustomerReply() {
        // Setup a ticket in Tenant 1
        authenticateUser(tenant1User);
        CreateTicketRequest request = new CreateTicketRequest("Support ticket", "Details", TicketPriority.MEDIUM);
        TicketResponse ticketRes = ticketService.createTicket(request);

        notificationRepository.deleteAllInBatch(); // Clear creation notifications

        // Customer replies
        authenticateUser(tenant1User);
        CreateTicketMessageRequest messageReq = new CreateTicketMessageRequest("Customer reply");
        ticketMessageService.sendMessage(ticketRes.getId(), messageReq);

        List<Notification> notifications = notificationRepository.findAll();
        // No assignee set -> broadcast to all tenant 1 moderators
        assertEquals(2, notifications.size());
        assertEquals(NotificationType.TICKET_MESSAGE, notifications.get(0).getNotificationType());
        assertEquals("Customer Replied", notifications.get(0).getTitle());
        assertEquals("Customer replied to a support ticket.", notifications.get(0).getMessage());
    }

    @Test
    public void testTicketMessageNotification_ModeratorReply() {
        // Setup ticket
        authenticateUser(tenant1User);
        CreateTicketRequest request = new CreateTicketRequest("Support ticket", "Details", TicketPriority.MEDIUM);
        TicketResponse ticketRes = ticketService.createTicket(request);

        notificationRepository.deleteAllInBatch();

        // Moderator replies
        authenticateUser(tenant1Moderator);
        CreateTicketMessageRequest messageReq = new CreateTicketMessageRequest("Moderator reply");
        ticketMessageService.sendMessage(ticketRes.getId(), messageReq);

        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size()); // Notify customer creator
        assertEquals(tenant1User.getId(), notifications.get(0).getRecipient().getId());
        assertEquals(NotificationType.TICKET_MESSAGE, notifications.get(0).getNotificationType());
        assertEquals("Support Replied", notifications.get(0).getTitle());
        assertEquals("Support replied to your ticket.", notifications.get(0).getMessage());
    }

    @Test
    public void testTenantIsolation_NotificationsRestricted() {
        // T2 moderator replies to a T1 ticket message -> should fail authorization.
        // We verify that creators/moderators from Tenant 2 do not receive notifications from Tenant 1 events.
        authenticateUser(tenant1User);
        CreateTicketRequest request = new CreateTicketRequest("Tenant 1 Ticket", "Details", TicketPriority.MEDIUM);
        TicketResponse ticketRes = ticketService.createTicket(request);

        notificationRepository.deleteAllInBatch();

        // Customer replies
        authenticateUser(tenant1User);
        CreateTicketMessageRequest messageReq = new CreateTicketMessageRequest("Tenant 1 Customer reply");
        ticketMessageService.sendMessage(ticketRes.getId(), messageReq);

        List<Notification> notifications = notificationRepository.findAll();
        for (Notification notification : notifications) {
            // Verify notifications are only for Tenant 1 users
            assertEquals(tenant1.getId(), notification.getTenant().getId());
            assertNotEquals(tenant2User.getId(), notification.getRecipient().getId());
            assertNotEquals(tenant2Moderator.getId(), notification.getRecipient().getId());
        }
    }

    @Test
    public void testTransactionRollback_NotificationsRemoved() {
        authenticateUser(tenant1Answerer);

        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute((TransactionStatus status) -> {
                CreateAnswerRequest request = new CreateAnswerRequest("Failing answer");
                answerService.createAnswer(tenant1Question.getId(), request);
                throw new RuntimeException("Force Rollback");
            });
        });

        // Verify that neither the answer nor the notification was persisted due to rollback
        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.isEmpty(), "Notifications must be rolled back on runtime exceptions");
    }
}

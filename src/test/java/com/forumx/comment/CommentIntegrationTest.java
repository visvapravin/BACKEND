package com.forumx.comment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import com.forumx.ForumXApplication;
import com.forumx.comment.dto.request.CreateCommentRequest;
import com.forumx.comment.dto.response.CommentResponse;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.comment.service.CommentService;
import com.forumx.answer.repository.AnswerRepository;
import com.forumx.answer.entity.Answer;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.question.entity.Question;
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
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.repository.TicketMessageRepository;
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
import org.springframework.security.access.AccessDeniedException;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class)
public class CommentIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

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
    private com.forumx.moderation.repository.ModerationReportRepository reportRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private AuthenticationFacade authenticationFacade;

    @MockBean
    private TenantResolver tenantResolver;

    private Tenant tenant1;
    private Tenant tenant2;

    private User t1QuestionOwner;
    private User t1Answerer;
    private User t2User;

    private Question t1Question;
    private Answer t1Answer;

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

        // 1. Setup Tenants (lookup or save to prevent unique constraint failures)
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

        // 3. Setup Users
        t1QuestionOwner = userRepository.save(User.builder()
                .username("t1_q_owner")
                .email("t1_q_owner@test.com")
                .tenant(tenant1)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(t1QuestionOwner).role(userRole).active(true).build());

        t1Answerer = userRepository.save(User.builder()
                .username("t1_answerer")
                .email("t1_answerer@test.com")
                .tenant(tenant1)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(t1Answerer).role(userRole).active(true).build());

        t2User = userRepository.save(User.builder()
                .username("t2_user")
                .email("t2_user@test.com")
                .tenant(tenant2)
                .enabled(true)
                .build());
        userRoleRepository.save(UserRole.builder().user(t2User).role(userRole).active(true).build());

        // Refresh users from DB eagerly with full profile and roles to prevent LazyInitializationException
        t1QuestionOwner = userRepository.findByIdWithFullProfile(t1QuestionOwner.getId()).orElseThrow();
        t1Answerer = userRepository.findByIdWithFullProfile(t1Answerer.getId()).orElseThrow();
        t2User = userRepository.findByIdWithFullProfile(t2User.getId()).orElseThrow();

        // 4. Setup Question & Answer
        t1Question = questionRepository.save(Question.builder()
                .title("T1 Question Title")
                .content("Question Content")
                .author(t1QuestionOwner)
                .tenant(tenant1)
                .build());

        t1Answer = answerRepository.save(Answer.builder()
                .question(t1Question)
                .author(t1Answerer)
                .content("Here is a very long and detailed answer to satisfy validation constraints.")
                .tenant(tenant1)
                .build());
    }

    private void authenticateUser(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
        when(tenantResolver.resolveTenantId()).thenReturn(user.getTenant().getId());
    }

    @Test
    public void testQuestionComment_Notification_Success() {
        authenticateUser(t1Answerer);

        CreateCommentRequest request = new CreateCommentRequest("Great question!");
        CommentResponse response = commentService.createForQuestion(t1Question.getId(), request);

        assertNotNull(response);
        assertEquals("Great question!", response.getContent());

        // Verify notification was sent to Question Owner
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        Notification notification = notifications.get(0);
        assertEquals(t1QuestionOwner.getId(), notification.getRecipient().getId());
        assertEquals(t1Answerer.getId(), notification.getActor().getId());
        assertEquals(NotificationType.QUESTION_COMMENTED, notification.getNotificationType());
        assertEquals("New Comment", notification.getTitle());
        assertEquals("t1_answerer commented on your question.", notification.getMessage());
    }

    @Test
    public void testAnswerComment_Notification_Success() {
        authenticateUser(t1QuestionOwner);

        CreateCommentRequest request = new CreateCommentRequest("Helpful answer, thanks!");
        CommentResponse response = commentService.createForAnswer(t1Answer.getId(), request);

        assertNotNull(response);
        assertEquals("Helpful answer, thanks!", response.getContent());

        // Verify notification was sent to Answer Author
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        Notification notification = notifications.get(0);
        assertEquals(t1Answerer.getId(), notification.getRecipient().getId());
        assertEquals(t1QuestionOwner.getId(), notification.getActor().getId());
        assertEquals(NotificationType.QUESTION_COMMENTED, notification.getNotificationType());
        assertEquals("New Comment", notification.getTitle());
        assertEquals("t1_q_owner commented on your answer.", notification.getMessage());
    }

    @Test
    public void testCommentCreation_SelfComment_NoNotification() {
        authenticateUser(t1QuestionOwner);

        CreateCommentRequest request = new CreateCommentRequest("Self comment on my question");
        commentService.createForQuestion(t1Question.getId(), request);

        // Verify no notification is sent
        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.isEmpty());
    }

    @Test
    public void testTenantIsolation_CrossTenantCommenting_ThrowsAccessDenied() {
        authenticateUser(t2User);

        CreateCommentRequest request = new CreateCommentRequest("Trying to comment on another tenant question");
        assertThrows(AccessDeniedException.class, () -> {
            commentService.createForQuestion(t1Question.getId(), request);
        });
    }

    @Test
    public void testTransactionRollback_CommentAndNotificationRemoved() {
        authenticateUser(t1Answerer);

        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute((TransactionStatus status) -> {
                CreateCommentRequest request = new CreateCommentRequest("Failing transaction comment");
                commentService.createForQuestion(t1Question.getId(), request);
                throw new RuntimeException("Force Rollback");
            });
        });

        // Verify comment and notification were not saved
        assertTrue(commentRepository.findAll().isEmpty());
        assertTrue(notificationRepository.findAll().isEmpty());
    }
}

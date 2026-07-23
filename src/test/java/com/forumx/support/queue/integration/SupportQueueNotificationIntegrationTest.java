package com.forumx.support.queue.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = "forumx.messaging.enabled=true")
public class SupportQueueNotificationIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.forumx.auth.repository.UserRoleRepository userRoleRepository;

    @Autowired
    private com.forumx.auth.repository.RoleRepository roleRepository;


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
    private com.forumx.support.ticket.repository.TicketRepository ticketRepository;

    @Autowired
    private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;

    @Autowired
    private com.forumx.comment.repository.CommentRepository commentRepository;

    @Autowired
    private com.forumx.moderation.repository.ModerationReportRepository reportRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @SpyBean
    private NotificationApplicationService notificationApplicationService;

    @MockBean
    private com.forumx.security.facade.AuthenticationFacade authenticationFacade;

    @MockBean
    private com.forumx.redis.gateway.RedisGateway redisGateway;

    @MockBean
    private com.forumx.tenant.resolver.TenantResolver tenantResolver;


    private Tenant tenant;
    private User creator;
    private User moderator;

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
                .username("creator_user")
                .email("creator@test.com")
                .tenant(tenant)
                .enabled(true)
                .build());

        moderator = userRepository.save(User.builder()
                .username("moderator_user")
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
    }


    @Test
    public void testNotificationTriggeredOnTicketCreationInBusinessLayer() {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(
                transactionTemplate.execute(status ->
                        new com.forumx.security.model.CustomUserDetails(
                                userRepository.findByIdWithFullProfile(creator.getId()).orElseThrow())));


        CreateTicketRequest request = new CreateTicketRequest();
        request.setSubject("Notification Test Ticket");
        request.setDescription("Ticket content");
        request.setPriority(TicketPriority.HIGH);

        transactionTemplate.execute(status -> ticketService.createTicket(request));

        // Verify that notification application service was invoked cleanly in business layer
        verify(notificationApplicationService, atLeastOnce()).notifyTicketCreated(
                anyLong(), anyLong(), anyLong(), anyLong()
        );

        assertTrue(notificationRepository.count() >= 1);
    }
}

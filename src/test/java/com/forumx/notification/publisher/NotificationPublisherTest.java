package com.forumx.notification.publisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.messaging.gateway.EventGateway;
import com.forumx.messaging.constant.MessagingExchanges;
import com.forumx.messaging.constant.MessagingRoutingKeys;
import com.forumx.notification.api.NotificationCommand;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = "forumx.messaging.enabled=true")
public class NotificationPublisherTest {

    @Autowired
    private NotificationApplicationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
    private com.forumx.support.ticket.repository.TicketRepository ticketRepository;

    @Autowired
    private com.forumx.support.ticket.repository.TicketMessageRepository ticketMessageRepository;

    @Autowired
    private com.forumx.comment.repository.CommentRepository commentRepository;

    @Autowired
    private com.forumx.moderation.repository.ModerationReportRepository reportRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private EventGateway eventGateway;

    @MockBean
    private AuthenticationFacade authenticationFacade;

    @MockBean
    private TenantResolver tenantResolver;

    private Tenant tenant;
    private User recipient;

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

        recipient = userRepository.save(User.builder()
                .username("test_recipient")
                .email("recipient@test.com")
                .tenant(tenant)
                .enabled(true)
                .build());
    }

    @Test
    public void testPublishEventAfterCommit_Success() {
        transactionTemplate.execute((TransactionStatus status) -> {
            NotificationCommand command = new NotificationCommand(
                    tenant.getId(),
                    recipient.getId(),
                    null,
                    NotificationType.ANSWER_CREATED,
                    "New Answer",
                    "A reply was created",
                    ReferenceType.QUESTION,
                    999L
            );
            notificationService.create(command);
            
            // Should not be published yet because transaction is not committed
            verify(eventGateway, never()).publish(anyString(), anyString(), any());
            return null;
        });

        // After commit, verify publishing
        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway, timeout(2000).times(1)).publish(
                eq(MessagingExchanges.USER_EVENTS_EXCHANGE),
                eq(MessagingRoutingKeys.NOTIFICATION_CREATED),
                envelopeCaptor.capture()
        );

        EventEnvelope<?> envelope = envelopeCaptor.getValue();
        assertNotNull(envelope);
        assertEquals("NOTIFICATION_CREATED", envelope.eventType());
        assertEquals(tenant.getId(), envelope.tenantId());
    }

    @Test
    public void testNoPublishOnRollback() {
        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute((TransactionStatus status) -> {
                NotificationCommand command = new NotificationCommand(
                        tenant.getId(),
                        recipient.getId(),
                        null,
                        NotificationType.ANSWER_CREATED,
                        "New Answer",
                        "A reply was created",
                        ReferenceType.QUESTION,
                        999L
                );
                notificationService.create(command);
                throw new RuntimeException("Force Rollback");
            });
        });

        // Verification: should never publish since transaction rolled back
        verify(eventGateway, never()).publish(anyString(), anyString(), any());
    }
}

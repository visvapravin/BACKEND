package com.forumx.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.notification.api.NotificationCommand;
import com.forumx.notification.entity.NotificationType;
import com.forumx.notification.entity.ReferenceType;
import com.forumx.notification.listener.NotificationEventSubscriber;
import com.forumx.notification.repository.NotificationRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
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
public class NotificationRealtimeIntegrationTest {

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
    private com.forumx.question.repository.QuestionRepository questionRepository;

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
    private NotificationEventSubscriber notificationEventSubscriber;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private RealtimeGateway realtimeGateway;

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
                .username("realtime_user")
                .email("realtime@test.com")
                .tenant(tenant)
                .enabled(true)
                .build());
    }

    @Test
    public void testEndToEndNotificationCreationToRealtimeDelivery() {
        // 1. Create a notification inside transaction
        NotificationCommand command = new NotificationCommand(
                tenant.getId(),
                recipient.getId(),
                null,
                NotificationType.ANSWER_CREATED,
                "New Answer E2E",
                "WebSocket delivery content",
                ReferenceType.QUESTION,
                777L
        );

        transactionTemplate.execute(status -> {
            notificationService.create(command);
            return null;
        });

        // Verify the notification was saved in Database
        assertEquals(1, notificationRepository.count());

        // 2. Simulate RabbitMQ consumption (since we mock RealtimeGateway, we just call the subscriber directly with the produced data type or verify that the listener receives it)
        var notificationEntity = notificationRepository.findAll().get(0);
        
        com.forumx.notification.event.NotificationCreatedEvent event = new com.forumx.notification.event.NotificationCreatedEvent(
                notificationEntity.getId(),
                recipient.getId(),
                recipient.getUsername(),
                tenant.getId(),
                notificationEntity.getNotificationType().name(),
                notificationEntity.getTitle(),
                notificationEntity.getMessage(),
                null,
                notificationEntity.getReferenceType().name(),
                notificationEntity.getReferenceId(),
                java.time.Instant.now()
        );

        EventEnvelope<com.forumx.notification.event.NotificationCreatedEvent> envelope = EventEnvelope.of(
                "NOTIFICATION_CREATED",
                tenant.getId(),
                "notification-service",
                event
        );

        notificationEventSubscriber.onNotificationCreated(envelope);

        // 3. Verify RealtimeGateway was invoked
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.forumx.websocket.dto.RealtimeEvent<Object>> realtimeEventCaptor = ArgumentCaptor.forClass(com.forumx.websocket.dto.RealtimeEvent.class);
        verify(realtimeGateway, times(1)).sendToUser(
                eq(recipient.getUsername()),
                eq("/queue/notifications"),
                realtimeEventCaptor.capture()
        );

        var capturedRealtimeEvent = realtimeEventCaptor.getValue();
        assertNotNull(capturedRealtimeEvent);
        assertEquals("NOTIFICATION_CREATED", capturedRealtimeEvent.getType());
        
        com.forumx.notification.dto.response.NotificationResponse responsePayload = (com.forumx.notification.dto.response.NotificationResponse) capturedRealtimeEvent.getPayload();
        assertEquals("New Answer E2E", responsePayload.getTitle());
        assertEquals("WebSocket delivery content", responsePayload.getMessage());
    }
}

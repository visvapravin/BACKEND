package com.forumx.notification.email.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.dto.request.RegisterRequest;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.service.AuthenticationService;
import com.forumx.notification.email.config.MailProperties;
import com.forumx.presence.service.PresenceService;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.service.ChatService;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.forumx.messaging.dto.EventEnvelope;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = {"forumx.messaging.enabled=true", "management.health.mail.enabled=false"})
public class NotificationEmailIntegrationTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private PresenceService presenceService;

    @MockBean
    private com.forumx.websocket.gateway.RealtimeGateway realtimeGateway;

    @MockBean
    private com.forumx.redis.gateway.RedisGateway redisGateway;

    @MockBean
    private com.forumx.security.facade.AuthenticationFacade authenticationFacade;

    @MockBean
    private com.forumx.tenant.resolver.TenantResolver tenantResolver;

    @MockBean
    private com.forumx.messaging.gateway.EventGateway eventGateway;

    @Autowired
    private com.forumx.notification.email.listener.EmailEventSubscriber emailEventSubscriber;

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
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private com.forumx.support.chat.repository.ChatMessageRepository chatMessageRepository;

    private Tenant tenant;
    private User customer;
    private User moderator;

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
                    .username("offline_cust")
                    .email("offline@cust.com")
                    .tenant(tenant)
                    .enabled(true)
                    .build());

            moderator = userRepository.save(User.builder()
                    .username("online_mod")
                    .email("mod@chat.com")
                    .tenant(tenant)
                    .enabled(true)
                    .build());

            return null;
        });

        customer = userRepository.findByIdWithFullProfile(customer.getId()).orElseThrow();

        reset(mailSender);
        mailProperties.setEnabled(true);

        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
    }

    @Test
    public void testUserRegistrationTriggersVerificationEmail() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("async_reg_user");
        request.setEmail("async@reg.com");
        request.setPassword("Password123!");
        request.setConfirmPassword("Password123!");
        request.setTenantSlug("default");

        authenticationService.register(request);

        // Capture published EventEnvelope
        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway, timeout(5000)).publish(
                eq(com.forumx.messaging.constant.MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(com.forumx.messaging.constant.MessagingRoutingKeys.EMAIL_SEND),
                envelopeCaptor.capture()
        );

        // Simulate RabbitMQ delivery
        emailEventSubscriber.onEmailSendEvent(envelopeCaptor.getValue());

        // Verify registration automatically dispatches asynchronous verification email
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    public void testOfflineChatMessageQueuesAndSendsEmail() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Stub presence to report recipient offline
        when(presenceService.isOnline(customer.getId())).thenReturn(false);

        // 1. Create ticket and session
        Ticket ticket = transactionTemplate.execute(status -> ticketRepository.save(Ticket.builder()
                .tenant(tenant)
                .creator(customer)
                .assignedTo(moderator)
                .subject("Support needed")
                .description("Offline recipient test")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .version(0L)
                .build()));

        transactionTemplate.execute(status -> chatSessionRepository.save(ChatSession.builder()
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .moderator(moderator)
                .status(ChatSessionStatus.ACTIVE)
                .version(0L)
                .build()));

        // 2. Moderator sends a message -> recipient (customer) is offline
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(new com.forumx.security.model.CustomUserDetails(moderator));

        SendMessageRequest sendRequest = new SendMessageRequest("Hello customer, are you there?");
        transactionTemplate.execute(status -> {
            chatService.sendMessage(ticket.getId(), sendRequest);
            return null;
        });

        // Capture published EventEnvelope
        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor2 = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway, timeout(5000)).publish(
                eq(com.forumx.messaging.constant.MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(com.forumx.messaging.constant.MessagingRoutingKeys.EMAIL_SEND),
                envelopeCaptor2.capture()
        );

        // Simulate RabbitMQ delivery
        emailEventSubscriber.onEmailSendEvent(envelopeCaptor2.getValue());

        // Verify SMTP mail dispatch for offline recipient
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertNotNull(captor.getValue());
    }
}

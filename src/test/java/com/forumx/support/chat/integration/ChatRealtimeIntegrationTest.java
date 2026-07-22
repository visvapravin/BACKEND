package com.forumx.support.chat.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.repository.UserRepository;
import com.forumx.messaging.dto.EventEnvelope;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.listener.ChatEventSubscriber;
import com.forumx.support.chat.service.ChatService;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import java.time.Instant;
import java.util.UUID;
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
public class ChatRealtimeIntegrationTest {

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ChatService chatService;
    @Autowired private ChatSessionService chatSessionService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private com.forumx.auth.repository.RoleRepository roleRepository;
    @Autowired private com.forumx.auth.repository.UserRoleRepository userRoleRepository;
    @Autowired private com.forumx.support.chat.repository.ChatSessionRepository chatSessionRepository;
    @Autowired private com.forumx.support.chat.repository.ChatMessageRepository chatMessageRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private ChatEventSubscriber chatEventSubscriber;

    @MockBean private RealtimeGateway realtimeGateway;
    @MockBean private com.forumx.redis.gateway.RedisGateway redisGateway;
    @MockBean private com.forumx.security.facade.AuthenticationFacade authenticationFacade;
    @MockBean private com.forumx.tenant.resolver.TenantResolver tenantResolver;

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
    @SuppressWarnings("unchecked")
    public void testSendMessageAndSubscriberBroadcast() {
        when(tenantResolver.resolveTenantId()).thenReturn(tenant.getId());
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);

        // 1. Create a support ticket
        CreateTicketRequest createRequest = new CreateTicketRequest();
        createRequest.setSubject("Chat Issue");
        createRequest.setDescription("WebSocket connection issues");
        createRequest.setPriority(TicketPriority.HIGH);

        TicketResponse ticketResponse = transactionTemplate.execute(status -> ticketService.createTicket(createRequest));
        assertNotNull(ticketResponse);

        // 2. Assign the ticket to moderator so a session can be opened
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        com.forumx.support.ticket.dto.request.AssignTicketRequest assignRequest = new AssignTicketRequest();
        assignRequest.setAssignedToUserId(moderator.getId());
        transactionTemplate.execute(status -> ticketService.assignTicket(ticketResponse.getId(), assignRequest));

        // 3. Customer sends a chat message
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        SendMessageRequest sendRequest = new SendMessageRequest("Is anyone there?");
        ChatMessage savedMessage = transactionTemplate.execute(status -> chatService.sendMessage(ticketResponse.getId(), sendRequest));
        assertNotNull(savedMessage);

        ChatSession session = chatSessionService.getSessionByTicketId(ticketResponse.getId(), tenant.getId());

        // 4. Simulate RabbitMQ Delivery to ChatEventSubscriber
        ChatMessageSentEvent sentEvent = new ChatMessageSentEvent(
                UUID.randomUUID(),
                tenant.getId(),
                session.getId(),
                ticketResponse.getId(),
                customer.getId(),
                customer.getUsername(),
                savedMessage.getId(),
                "Is anyone there?",
                "TEXT",
                Instant.now()
        );

        EventEnvelope<ChatMessageSentEvent> envelope = EventEnvelope.of(
                "CHAT_MESSAGE_SENT", tenant.getId(), "support-chat-service", sentEvent
        );

        chatEventSubscriber.onChatEvent(envelope);

        // 5. Verify WebSocket broadcast to /topic/tenants/{tenantId}/chat/{sessionId}
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<ChatMessageSentEvent>> captor = ArgumentCaptor.forClass(RealtimeEvent.class);
        String expectedTopic = "/topic/tenants/" + tenant.getId() + "/chat/" + session.getId();
        verify(realtimeGateway).sendToTopic(eq(expectedTopic), captor.capture());

        RealtimeEvent<ChatMessageSentEvent> broadcastEvent = captor.getValue();
        assertEquals("CHAT_MESSAGE_SENT", broadcastEvent.getType());
        ChatMessageSentEvent payload = (ChatMessageSentEvent) broadcastEvent.getPayload();
        assertEquals("Is anyone there?", payload.content());
        assertEquals(customer.getId(), payload.senderId());
    }
}

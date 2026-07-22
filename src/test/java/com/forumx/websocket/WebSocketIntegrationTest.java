package com.forumx.websocket;

import static org.junit.jupiter.api.Assertions.*;

import com.forumx.ForumXApplication;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.forumx.auth.repository.RefreshTokenRepository;
import com.forumx.auth.repository.RoleRepository;
import com.forumx.auth.repository.UserProfileRepository;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.auth.verification.repository.VerificationTokenRepository;
import com.forumx.bookmark.repository.BookmarkRepository;
import com.forumx.comment.repository.CommentRepository;
import com.forumx.question.repository.QuestionRepository;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.repository.TicketMessageRepository;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.chat.repository.ChatMessageRepository;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.websocket.constant.WebSocketDestinations;
import com.forumx.websocket.dto.RealtimeEvent;
import com.forumx.websocket.gateway.RealtimeGateway;
import java.lang.reflect.Type;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(classes = ForumXApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private com.forumx.security.jwt.JwtTokenProvider jwtTokenProvider;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private com.forumx.notification.repository.NotificationRepository notificationRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private ChatSessionRepository chatSessionRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketMessageRepository ticketMessageRepository;
    @Autowired private com.forumx.answer.repository.AnswerRepository answerRepository;
    @Autowired private com.forumx.moderation.repository.ModerationReportRepository reportRepository;
    @Autowired private RealtimeGateway realtimeGateway;
    @Autowired private ObjectMapper objectMapper;

    private String validToken;
    private User testUser;
    private Tenant tenant;
    private WebSocketStompClient stompClient;

    @BeforeEach
    public void setUp() {
        // Complete DB cleanup
        ticketMessageRepository.deleteAllInBatch();
        reportRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        bookmarkRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        chatMessageRepository.deleteAllInBatch();
        chatSessionRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        answerRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        verificationTokenRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userRoleRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // 1. Setup Tenant – reuse the 'default' slug so HttpTenantResolver finds this tenant
        // when called from the STOMP layer (where no HTTP request context is available,
        // HttpTenantResolver falls back to the 'default' slug database lookup).
        tenant = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("default"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Default Tenant")
                        .slug("default")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .build()));

        // 2. Setup Role
        Role userRole = roleRepository.findByRoleName(RoleType.USER)
                .orElseGet(() -> roleRepository.save(Role.builder().roleName(RoleType.USER).active(true).build()));

        // 3. Setup User & Token
        testUser = userRepository.save(User.builder()
                .username("ws_user")
                .email("ws_user@test.com")
                .tenant(tenant)
                .enabled(true)
                .status(User.UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(UserRole.builder().user(testUser).role(userRole).active(true).build());

        testUser = userRepository.findByIdWithFullProfile(testUser.getId()).orElseThrow();
        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        validToken = jwtTokenProvider.generateAccessToken(userDetails);

        // 4. Configure WebSocket STOMP client (plain WebSocket, no SockJS wrapper)
        // SockJS performs an HTTP /info probe with CORS origin checks that fail in test environments.
        // The raw WebSocket endpoint accepts connections without an Origin header restriction.
        //
        // Use the Spring Boot-configured ObjectMapper (which includes JavaTimeModule) so the
        // client-side converter can deserialize RealtimeEvent.timestamp (java.time.Instant).
        // A plain new MappingJackson2MessageConverter() uses a bare ObjectMapper without
        // JavaTimeModule, which causes silent deserialization failures for Instant fields.
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(converter);
    }

    private String getWsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    @Test
    public void testSuccessfulConnectionAndSubscription() throws InterruptedException, ExecutionException, TimeoutException {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + validToken);

        // When
        StompSession session = stompClient.connectAsync(getWsUrl(), (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        // Then
        assertTrue(session.isConnected());

        // Cleanup
        session.disconnect();
    }

    @Test
    public void testFailedConnection_InvalidToken() {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer invalidTokenString");

        // When & Then (should fail to connect due to invalid token)
        assertThrows(ExecutionException.class, () -> {
            stompClient.connectAsync(getWsUrl(), (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    public void testFailedConnection_MissingToken() {
        // Given (no headers at all)
        StompHeaders connectHeaders = new StompHeaders();

        // When & Then
        assertThrows(ExecutionException.class, () -> {
            stompClient.connectAsync(getWsUrl(), (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    public void testMessageTransmissionAndGatewayAbstraction() throws InterruptedException, ExecutionException, TimeoutException {
        // Given
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + validToken);

        StompSession session = stompClient.connectAsync(getWsUrl(), (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<RealtimeEvent<String>> blockingQueue = new ArrayBlockingQueue<>(1);

        // Subscribe to public notifications topic
        session.subscribe(WebSocketDestinations.TOPIC_NOTIFICATIONS, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RealtimeEvent.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.offer((RealtimeEvent<String>) payload);
            }
        });

        // Allow the SUBSCRIBE frame to be processed by the broker before publishing.
        // Without this, the message may be dispatched before the subscription is active.
        Thread.sleep(500);

        // When (gateway broadcasts to the topic)
        RealtimeEvent<String> event = RealtimeEvent.<String>builder()
                .type("NOTIFICATION")
                .payload("New Support Ticket received!")
                .build();

        realtimeGateway.sendToTopic(WebSocketDestinations.TOPIC_NOTIFICATIONS, event);

        // Then
        RealtimeEvent<String> receivedEvent = blockingQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedEvent);
        assertEquals(event.getEventId(), receivedEvent.getEventId());
        assertEquals("NOTIFICATION", receivedEvent.getType());
        assertEquals("New Support Ticket received!", receivedEvent.getPayload());

        // Cleanup
        session.disconnect();
    }
}

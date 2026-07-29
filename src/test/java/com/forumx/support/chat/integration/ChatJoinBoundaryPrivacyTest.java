package com.forumx.support.chat.integration;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.repository.ChatMessageRepository;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.ChatPermissionService;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.support.chat.service.impl.ChatServiceImpl;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import com.forumx.websocket.exception.WebSocketAuthenticationException;
import com.forumx.websocket.security.AuthChannelInterceptor;
import com.forumx.websocket.session.WebSocketSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatJoinBoundaryPrivacyTest {

    @Mock
    private TenantResolver tenantResolver;
    @Mock
    private AuthenticationFacade authenticationFacade;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private SupportSessionParticipantRepository participantRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatPermissionService chatPermissionService;
    @Mock
    private ChatSessionService chatSessionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private ChatServiceImpl chatService;
    @InjectMocks
    private AuthChannelInterceptor authChannelInterceptor;

    private Tenant tenant;
    private User customer;
    private User moderatorA;
    private User moderatorB;
    private Ticket ticket;
    private ChatSession chatSession;
    private CustomUserDetails moderatorBDetails;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("Default").slug("default").build();
        customer = User.builder().id(10L).username("customer_a").email("customer_a@dev.com").tenant(tenant).build();
        moderatorA = User.builder().id(20L).username("moderator_a").email("moderator_a@dev.com").tenant(tenant).build();
        moderatorB = User.builder().id(30L).username("moderator_b").email("moderator_b@dev.com").tenant(tenant).build();

        ticket = Ticket.builder().id(128L).tenant(tenant).createdBy("10").assignedTo(moderatorA).build();
        chatSession = ChatSession.builder().id(43L).ticket(ticket).tenant(tenant).customer(customer).moderator(moderatorA).status(ChatSessionStatus.ACTIVE).build();

        moderatorBDetails = new CustomUserDetails(moderatorB);
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE is rejected for inactive moderator attempting to subscribe to private chat topic")
    void testStompSubscribeRejectedForInactiveModerator() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tenants/1/chat/43");

        WebSocketSessionContext context = WebSocketSessionContext.builder()
                .sessionId("ws-session-123")
                .userId(moderatorB.getId())
                .username(moderatorB.getUsername())
                .tenantId(1L)
                .roles(Collections.singletonList("ROLE_MODERATOR"))
                .build();

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionContext.SESSION_KEY, context);
        accessor.setSessionAttributes(sessionAttributes);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(participantRepository.findBySession_IdAndUser_IdAndIsActiveTrue(43L, moderatorB.getId()))
                .thenReturn(Optional.empty());

        WebSocketAuthenticationException ex = assertThrows(
                WebSocketAuthenticationException.class,
                () -> authChannelInterceptor.preSend(message, messageChannel)
        );

        assertTrue(ex.getMessage().contains("User is not an active participant in this support room"));
    }

    @Test
    @DisplayName("Inactive moderator cannot send message")
    void testInactiveModeratorCannotSend() {
        lenient().when(tenantResolver.resolveTenantId()).thenReturn(1L);
        lenient().when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorBDetails);
        lenient().when(userRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(moderatorB));
        lenient().when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(128L, 1L)).thenReturn(Optional.of(ticket));
        lenient().when(chatSessionService.getSessionByTicketId(128L, 1L)).thenReturn(chatSession);
        doThrow(new AccessDeniedException("User is not an active participant in this chat session"))
                .when(chatPermissionService).assertCanSend(any(), eq(30L));

        SendMessageRequest request = new SendMessageRequest("Test Message");
        assertThrows(AccessDeniedException.class, () -> chatService.sendMessage(128L, request));
    }

    @Test
    @DisplayName("REST history uses findAuthorizedMessages filtering active participation windows")
    void testRestHistoryFiltersByActiveParticipationWindows() {
        lenient().when(tenantResolver.resolveTenantId()).thenReturn(1L);
        lenient().when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorBDetails);
        lenient().when(userRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(moderatorB));
        lenient().when(chatSessionService.getSessionByTicketId(128L, 1L)).thenReturn(chatSession);

        ChatMessage msgActive = ChatMessage.builder().id(101L).session(chatSession).sender(customer).content("MSG_ACTIVE").createdAt(Instant.now()).build();
        Page<ChatMessage> authorizedPage = new PageImpl<>(Collections.singletonList(msgActive));

        when(chatMessageRepository.findAuthorizedMessagesFirstPage(eq(43L), eq(30L), any()))
                .thenReturn(authorizedPage);

        Page<ChatMessage> result = chatService.getMessages(128L, null, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("MSG_ACTIVE", result.getContent().get(0).getContent());
        verify(chatMessageRepository).findAuthorizedMessagesFirstPage(eq(43L), eq(30L), any());
    }
}

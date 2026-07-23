package com.forumx.support.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.presence.service.PresenceService;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.chat.dto.request.SendMessageRequest;
import com.forumx.support.chat.entity.ChatMessage;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.MessageDeliveryStatus;
import com.forumx.support.chat.entity.MessageType;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.event.durable.ChatMessageDeletedEvent;
import com.forumx.support.chat.event.durable.ChatMessageReadEvent;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.repository.ChatMessageRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.impl.ChatServiceImpl;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock private ChatSessionService chatSessionService;
    @Mock private ChatPermissionService chatPermissionService;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private SupportSessionParticipantRepository participantRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantResolver tenantResolver;
    @Mock private AuthenticationFacade authenticationFacade;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PresenceService presenceService;
    @Mock private NotificationApplicationService notificationApplicationService;

    @InjectMocks private ChatServiceImpl chatService;

    private Tenant tenant;
    private User customer;
    private User moderator;
    private CustomUserDetails customerDetails;
    private CustomUserDetails moderatorDetails;
    private Ticket ticket;
    private ChatSession session;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).build();
        customer = User.builder().id(10L).username("customer").tenant(tenant).build();
        moderator = User.builder().id(20L).username("moderator").tenant(tenant).build();
        customerDetails = new CustomUserDetails(customer);
        moderatorDetails = new CustomUserDetails(moderator);

        ticket = Ticket.builder()
                .id(100L)
                .tenant(tenant)
                .creator(customer)
                .assignedTo(moderator)
                .build();

        session = ChatSession.builder()
                .id(200L)
                .ticket(ticket)
                .tenant(tenant)
                .customer(customer)
                .moderator(moderator)
                .build();
    }

    private void mockSecurityContext(User user, CustomUserDetails details) {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(details);
        when(userRepository.findByIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    public void testSendMessageSuccessfullyRecipientOffline() {
        mockSecurityContext(customer, customerDetails);
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));
        when(chatSessionService.getOrCreateSession(ticket, 1L)).thenReturn(session);
        
        SendMessageRequest request = new SendMessageRequest("Hello moderator");
        
        ChatMessage mockSaved = ChatMessage.builder()
                .id(500L)
                .session(session)
                .sender(customer)
                .messageType(MessageType.TEXT)
                .content("Hello moderator")
                .deliveryStatus(MessageDeliveryStatus.SENT)
                .build();
        
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(mockSaved);
        when(presenceService.isOnline(moderator.getId())).thenReturn(false);

        ChatMessage result = chatService.sendMessage(100L, request);

        assertNotNull(result);
        assertEquals("Hello moderator", result.getContent());
        assertEquals(500L, result.getId());

        // Verify event published
        ArgumentCaptor<ChatMessageSentEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageSentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ChatMessageSentEvent sentEvent = eventCaptor.getValue();
        assertEquals(500L, sentEvent.messageId());
        assertEquals("Hello moderator", sentEvent.content());

        // Verify offline notification triggered
        verify(notificationApplicationService).notifyChatMessageReceived(any());
        verify(chatPermissionService).assertCanSend(session, customer.getId());
    }

    @Test
    public void testCustomerGetsFullMessageHistory() {
        mockSecurityContext(customer, customerDetails);
        when(chatSessionService.getSessionByTicketId(100L, 1L)).thenReturn(session);
        
        PageRequest pageRequest = PageRequest.of(0, 50);
        Page<ChatMessage> page = new PageImpl<>(Collections.emptyList());
        when(chatMessageRepository.findMessagesFirstPage(session.getId(), pageRequest)).thenReturn(page);

        Page<ChatMessage> result = chatService.getMessages(100L, null, pageRequest);
        
        assertNotNull(result);
        verify(chatMessageRepository).findMessagesFirstPage(session.getId(), pageRequest);
        verify(chatMessageRepository, never()).findMessagesForParticipantFirstPage(any(), any(), any());
    }

    @Test
    public void testModeratorGetsJoinedAtFilteredMessageHistory() {
        mockSecurityContext(moderator, moderatorDetails);
        when(chatSessionService.getSessionByTicketId(100L, 1L)).thenReturn(session);

        Instant joinTime = Instant.now().minusSeconds(300);
        SupportSessionParticipant participant = SupportSessionParticipant.builder()
                .id(1L)
                .session(session)
                .tenant(tenant)
                .user(moderator)
                .role(ParticipantRole.MODERATOR)
                .joinedAt(joinTime)
                .isActive(true)
                .build();

        when(participantRepository.findTopBySession_Ticket_IdAndUser_IdOrderByJoinedAtDesc(100L, 20L))
                .thenReturn(Optional.of(participant));

        PageRequest pageRequest = PageRequest.of(0, 50);
        Page<ChatMessage> page = new PageImpl<>(Collections.emptyList());
        when(chatMessageRepository.findMessagesForParticipantFirstPage(session.getId(), joinTime, pageRequest)).thenReturn(page);

        Page<ChatMessage> result = chatService.getMessages(100L, null, pageRequest);

        assertNotNull(result);
        verify(chatMessageRepository).findMessagesForParticipantFirstPage(session.getId(), joinTime, pageRequest);
        verify(chatMessageRepository, never()).findMessagesFirstPage(any(), any());
    }

    @Test
    public void testModeratorNotJoinedReturnsEmptyPage() {
        mockSecurityContext(moderator, moderatorDetails);
        when(chatSessionService.getSessionByTicketId(100L, 1L)).thenReturn(session);
        when(participantRepository.findTopBySession_Ticket_IdAndUser_IdOrderByJoinedAtDesc(100L, 20L))
                .thenReturn(Optional.empty());

        PageRequest pageRequest = PageRequest.of(0, 50);
        Page<ChatMessage> result = chatService.getMessages(100L, null, pageRequest);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(chatMessageRepository, never()).findMessagesFirstPage(any(), any());
        verify(chatMessageRepository, never()).findMessagesForParticipantFirstPage(any(), any(), any());
    }
}

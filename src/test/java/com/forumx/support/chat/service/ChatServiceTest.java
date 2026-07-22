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
import com.forumx.support.chat.event.durable.ChatMessageDeletedEvent;
import com.forumx.support.chat.event.durable.ChatMessageReadEvent;
import com.forumx.support.chat.event.durable.ChatMessageSentEvent;
import com.forumx.support.chat.repository.ChatMessageRepository;
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
    private CustomUserDetails userDetails;
    private Ticket ticket;
    private ChatSession session;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).build();
        customer = User.builder().id(10L).username("customer").tenant(tenant).build();
        moderator = User.builder().id(20L).username("moderator").tenant(tenant).build();
        userDetails = new CustomUserDetails(customer);

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

    private void mockSecurityContext() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(userDetails);
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(customer));
    }

    @Test
    public void testSendMessageSuccessfullyRecipientOffline() {
        mockSecurityContext();
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
    public void testSendMessageSuccessfullyRecipientOnline() {
        mockSecurityContext();
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
        when(presenceService.isOnline(moderator.getId())).thenReturn(true);

        ChatMessage result = chatService.sendMessage(100L, request);

        assertNotNull(result);
        
        // Online recipient -> do NOT trigger offline notification
        verify(notificationApplicationService, never()).notifyChatMessageReceived(any());
    }

    @Test
    public void testDeleteMessageSuccessfully() {
        mockSecurityContext();
        ChatMessage message = ChatMessage.builder()
                .id(500L)
                .session(session)
                .sender(customer)
                .content("To be deleted")
                .deleted(false)
                .build();
        
        when(chatMessageRepository.findById(500L)).thenReturn(Optional.of(message));

        chatService.deleteMessage(500L);

        assertTrue(message.isDeleted());
        assertEquals("This message was deleted.", message.getContent());
        assertNotNull(message.getDeletedAt());

        verify(chatMessageRepository).save(message);

        ArgumentCaptor<ChatMessageDeletedEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(500L, eventCaptor.getValue().messageId());
        verify(chatPermissionService).assertCanDelete(session, customer.getId(), message);
    }

    @Test
    public void testMarkReadSuccessfully() {
        mockSecurityContext();
        when(chatSessionService.getSessionByTicketId(100L, 1L)).thenReturn(session);

        ChatMessage msg1 = ChatMessage.builder().id(501L).sender(moderator).deliveryStatus(MessageDeliveryStatus.SENT).build();
        msg1.setCreatedAt(Instant.now().minusSeconds(10));
        ChatMessage msg2 = ChatMessage.builder().id(502L).sender(moderator).deliveryStatus(MessageDeliveryStatus.SENT).build();
        msg2.setCreatedAt(Instant.now());

        when(chatMessageRepository.findBySession_IdAndSender_IdNotAndDeliveryStatusNotAndDeletedFalse(
                session.getId(), customer.getId(), MessageDeliveryStatus.READ
        )).thenReturn(List.of(msg1, msg2));

        chatService.markRead(100L);

        assertEquals(MessageDeliveryStatus.READ, msg1.getDeliveryStatus());
        assertEquals(MessageDeliveryStatus.READ, msg2.getDeliveryStatus());
        verify(chatMessageRepository).saveAll(anyList());

        ArgumentCaptor<ChatMessageReadEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageReadEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ChatMessageReadEvent readEvent = eventCaptor.getValue();
        assertEquals(session.getId(), readEvent.sessionId());
        assertEquals(msg2.getCreatedAt(), readEvent.upToTimestamp());
        verify(chatPermissionService).assertCanRead(session, customer.getId());
    }

    @Test
    public void testGetMessagesFirstPage() {
        mockSecurityContext();
        when(chatSessionService.getSessionByTicketId(100L, 1L)).thenReturn(session);
        
        PageRequest pageRequest = PageRequest.of(0, 50);
        Page<ChatMessage> page = new PageImpl<>(Collections.emptyList());
        when(chatMessageRepository.findMessagesFirstPage(session.getId(), pageRequest)).thenReturn(page);

        Page<ChatMessage> result = chatService.getMessages(100L, null, pageRequest);
        
        assertNotNull(result);
        verify(chatMessageRepository).findMessagesFirstPage(session.getId(), pageRequest);
        verify(chatPermissionService).assertCanRead(session, customer.getId());
    }

    @Test
    public void testGetMessagesBeforeIdCursor() {
        mockSecurityContext();
        when(chatSessionService.getSessionByTicketId(100L, 1L)).thenReturn(session);
        
        PageRequest pageRequest = PageRequest.of(0, 50);
        Page<ChatMessage> page = new PageImpl<>(Collections.emptyList());
        when(chatMessageRepository.findMessagesBefore(session.getId(), 500L, pageRequest)).thenReturn(page);

        Page<ChatMessage> result = chatService.getMessages(100L, 500L, pageRequest);
        
        assertNotNull(result);
        verify(chatMessageRepository).findMessagesBefore(session.getId(), 500L, pageRequest);
    }
}

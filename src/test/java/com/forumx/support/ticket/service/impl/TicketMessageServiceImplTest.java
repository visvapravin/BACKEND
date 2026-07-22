package com.forumx.support.ticket.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.forumx.auth.entity.Role;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.enums.RoleType;
import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.common.exception.TicketClosedException;
import com.forumx.common.exception.TicketNotFoundException;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.response.TicketMessageResponse;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketMessage;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.support.ticket.mapper.TicketMessageMapper;
import com.forumx.support.ticket.repository.TicketMessageRepository;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import com.forumx.notification.service.NotificationApplicationService;

@ExtendWith(MockitoExtension.class)
public class TicketMessageServiceImplTest {

    @Mock
    private TicketMessageRepository ticketMessageRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketMessageMapper ticketMessageMapper;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private TenantResolver tenantResolver;

    @Mock
    private NotificationApplicationService notificationApplicationService;

    @InjectMocks
    private TicketMessageServiceImpl ticketMessageService;

    private Tenant tenant;
    private User customer;
    private User otherCustomer;
    private User moderator;
    private Ticket ticket;
    private CustomUserDetails customerDetails;
    private CustomUserDetails otherCustomerDetails;
    private CustomUserDetails moderatorDetails;

    @BeforeEach
    public void setUp() {
        tenant = Tenant.builder().id(1L).name("Tenant 1").slug("tenant1").build();

        Role userRole = Role.builder().id(1L).roleName(RoleType.USER).active(true).build();
        Role moderatorRole = Role.builder().id(2L).roleName(RoleType.MODERATOR).active(true).build();

        customer = User.builder()
                .id(2L)
                .username("customer")
                .email("customer@test.com")
                .tenant(tenant)
                .enabled(true)
                .build();
        UserRole customerRoleRelation = UserRole.builder().user(customer).role(userRole).active(true).build();
        customer.setUserRoles(java.util.Set.of(customerRoleRelation));

        otherCustomer = User.builder()
                .id(3L)
                .username("other")
                .email("other@test.com")
                .tenant(tenant)
                .enabled(true)
                .build();
        UserRole otherRoleRelation = UserRole.builder().user(otherCustomer).role(userRole).active(true).build();
        otherCustomer.setUserRoles(java.util.Set.of(otherRoleRelation));

        moderator = User.builder()
                .id(4L)
                .username("moderator")
                .email("moderator@test.com")
                .tenant(tenant)
                .enabled(true)
                .build();
        UserRole moderatorRoleRelation = UserRole.builder().user(moderator).role(moderatorRole).active(true).build();
        moderator.setUserRoles(java.util.Set.of(moderatorRoleRelation));

        ticket = Ticket.builder()
                .id(100L)
                .tenant(tenant)
                .creator(customer)
                .subject("Support needed")
                .description("Detail")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .build();

        customerDetails = new CustomUserDetails(customer);
        otherCustomerDetails = new CustomUserDetails(otherCustomer);
        moderatorDetails = new CustomUserDetails(moderator);
    }

    @Test
    public void testSendMessageSuccess_CustomerOwnTicket() {
        CreateTicketMessageRequest request = new CreateTicketMessageRequest("Hello, I need help");
        
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));

        TicketMessage mappedMessage = TicketMessage.builder().message(request.getMessage()).build();
        when(ticketMessageMapper.toEntity(request)).thenReturn(mappedMessage);

        TicketMessage savedMessage = TicketMessage.builder()
                .id(500L)
                .ticket(ticket)
                .sender(customer)
                .message(request.getMessage())
                .messageUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        when(ticketMessageRepository.save(any(TicketMessage.class))).thenReturn(savedMessage);

        TicketMessageResponse responseDto = TicketMessageResponse.builder()
                .id(500L)
                .ticketId(100L)
                .senderId(2L)
                .senderName("customer")
                .message(request.getMessage())
                .messageUuid(savedMessage.getMessageUuid())
                .createdAt(savedMessage.getCreatedAt())
                .build();
        when(ticketMessageMapper.toResponse(savedMessage)).thenReturn(responseDto);

        when(userRepository.findUsersByTenantIdAndRoles(eq(1L), anyCollection()))
                .thenReturn(List.of(moderator));

        TicketMessageResponse result = ticketMessageService.sendMessage(100L, request);

        assertNotNull(result);
        assertEquals(500L, result.getId());
        assertEquals("Hello, I need help", result.getMessage());

        // Verify ticket's lastMessageAt got updated
        assertNotNull(ticket.getLastMessageAt());
        verify(ticketRepository).save(ticket);

        // Verify Customer Reply broadcast notification was sent
        verify(notificationApplicationService, times(1)).notifyCustomerReply(
                eq(1L),
                eq(moderator.getId()),
                eq(customer.getId()),
                eq(ticket.getId())
        );
    }

    @Test
    public void testSendMessage_ModeratorCanReply() {
        CreateTicketMessageRequest request = new CreateTicketMessageRequest("This is a moderator response");

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(moderatorDetails);
        when(userRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(moderator));
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));

        TicketMessage mappedMessage = TicketMessage.builder().message(request.getMessage()).build();
        when(ticketMessageMapper.toEntity(request)).thenReturn(mappedMessage);

        TicketMessage savedMessage = TicketMessage.builder()
                .id(501L)
                .ticket(ticket)
                .sender(moderator)
                .message(request.getMessage())
                .messageUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        when(ticketMessageRepository.save(any(TicketMessage.class))).thenReturn(savedMessage);

        TicketMessageResponse responseDto = TicketMessageResponse.builder()
                .id(501L)
                .ticketId(100L)
                .senderId(4L)
                .senderName("moderator")
                .message(request.getMessage())
                .build();
        when(ticketMessageMapper.toResponse(savedMessage)).thenReturn(responseDto);

        TicketMessageResponse result = ticketMessageService.sendMessage(100L, request);
        assertNotNull(result);
        verify(ticketRepository).save(ticket);

        // Verify Support Reply notification was sent to creator
        verify(notificationApplicationService, times(1)).notifySupportReply(
                eq(1L),
                eq(customer.getId()),
                eq(moderator.getId()),
                eq(ticket.getId())
        );
    }

    @Test
    public void testSendMessage_CustomerReply_WithAssignedModerator() {
        CreateTicketMessageRequest request = new CreateTicketMessageRequest("Assignee reply test");
        ticket.setAssignedTo(moderator);

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));

        TicketMessage mappedMessage = TicketMessage.builder().message(request.getMessage()).build();
        when(ticketMessageMapper.toEntity(request)).thenReturn(mappedMessage);

        TicketMessage savedMessage = TicketMessage.builder()
                .id(502L)
                .ticket(ticket)
                .sender(customer)
                .message(request.getMessage())
                .messageUuid(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        when(ticketMessageRepository.save(any(TicketMessage.class))).thenReturn(savedMessage);

        TicketMessageResponse responseDto = TicketMessageResponse.builder()
                .id(502L)
                .ticketId(100L)
                .senderId(2L)
                .message(request.getMessage())
                .build();
        when(ticketMessageMapper.toResponse(savedMessage)).thenReturn(responseDto);

        TicketMessageResponse result = ticketMessageService.sendMessage(100L, request);

        assertNotNull(result);
        verify(ticketRepository).save(ticket);

        // Verify only assigned moderator is notified, and no broadcast occurs
        verify(notificationApplicationService, times(1)).notifyCustomerReply(
                eq(1L),
                eq(moderator.getId()),
                eq(customer.getId()),
                eq(ticket.getId())
        );
        verifyNoMoreInteractions(notificationApplicationService);
    }

    @Test
    public void testSendMessage_CustomerCannotAccessOtherCustomerTicket() {
        CreateTicketMessageRequest request = new CreateTicketMessageRequest("Trying to access");

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(otherCustomerDetails);
        when(userRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(otherCustomer));
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class, () -> ticketMessageService.sendMessage(100L, request));
    }

    @Test
    public void testSendMessage_ClosedTicketRejection() {
        ticket.setStatus(TicketStatus.CLOSED);
        CreateTicketMessageRequest request = new CreateTicketMessageRequest("Message to closed ticket");

        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));

        assertThrows(TicketClosedException.class, () -> ticketMessageService.sendMessage(100L, request));
    }

    @Test
    public void testGetConversation_OldestFirstForced() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(customer));
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.of(ticket));

        Pageable requestPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TicketMessage> mockPage = new PageImpl<>(Collections.emptyList());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(ticketMessageRepository.findByTicketAndDeletedFalse(eq(ticket), pageableCaptor.capture()))
                .thenReturn(mockPage);

        ticketMessageService.getConversation(100L, requestPageable);

        Pageable resolvedPageable = pageableCaptor.getValue();
        assertEquals(Sort.Direction.ASC, resolvedPageable.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    public void testTenantIsolation_NotFoundForOtherTenantTicket() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(customer));
        // Simulate ticket not found in this tenant
        when(ticketRepository.findByIdAndTenant_IdAndDeletedFalse(100L, 1L)).thenReturn(Optional.empty());

        assertThrows(TicketNotFoundException.class, () -> ticketMessageService.sendMessage(100L, new CreateTicketMessageRequest("Hello")));
    }

    @Test
    public void testUserLookupFailure_ThrowsEntityNotFoundException() {
        when(tenantResolver.resolveTenantId()).thenReturn(1L);
        when(authenticationFacade.getCurrentUserDetails()).thenReturn(customerDetails);
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> ticketMessageService.sendMessage(100L, new CreateTicketMessageRequest("Hello")));
    }
}

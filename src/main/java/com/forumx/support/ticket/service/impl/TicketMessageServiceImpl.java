package com.forumx.support.ticket.service.impl;

import java.util.List;
import java.util.UUID;
import com.forumx.auth.entity.User;
import com.forumx.auth.entity.UserRole;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
import com.forumx.common.exception.TicketClosedException;
import com.forumx.common.exception.TicketNotFoundException;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.dto.request.CreateTicketMessageRequest;
import com.forumx.support.ticket.dto.response.TicketMessageResponse;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketMessage;
import com.forumx.support.ticket.mapper.TicketMessageMapper;
import com.forumx.support.ticket.repository.TicketMessageRepository;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.TicketMessageService;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.auth.enums.RoleType;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketMessageServiceImpl implements TicketMessageService {

    private final TicketMessageRepository ticketMessageRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TicketMessageMapper ticketMessageMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;
    private final NotificationApplicationService notificationApplicationService;

    @Override
    @Transactional
    public TicketMessageResponse sendMessage(Long ticketId, CreateTicketMessageRequest request) {
        CurrentUser current = resolveCurrentUser();
        Ticket ticket = loadAndValidateTicket(ticketId, current);

        // Encapsulate ticket status check inside Ticket entity
        if (!ticket.canAcceptMessages()) {
            throw new TicketClosedException("Cannot send message. Ticket is closed.");
        }

        TicketMessage ticketMessage = ticketMessageMapper.toEntity(request);
        ticketMessage.setTicket(ticket);
        ticketMessage.setSender(current.user());
        ticketMessage.setMessageUuid(UUID.randomUUID());
        ticketMessage.setInternalNote(false);

        TicketMessage savedMessage = ticketMessageRepository.save(ticketMessage);

        // Derive lastMessageAt by delegating message registration to the Ticket entity
        ticket.registerNewMessage(savedMessage);
        ticketRepository.save(ticket);

        // Send Notification
        sendTicketMessageNotification(ticket, savedMessage, current);

        return ticketMessageMapper.toResponse(savedMessage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketMessageResponse> getConversation(Long ticketId, Pageable pageable) {
        CurrentUser current = resolveCurrentUser();
        Ticket ticket = loadAndValidateTicket(ticketId, current);

        // Force oldest-first order (ascending by createdAt)
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        Page<TicketMessage> messages = ticketMessageRepository.findByTicketAndDeletedFalse(ticket, sortedPageable);
        return messages.map(ticketMessageMapper::toResponse);
    }

    private void sendTicketMessageNotification(Ticket ticket, TicketMessage savedMessage, CurrentUser current) {
        boolean isModerator = elevated(current.details());
        if (isModerator) {
            // Moderator replied -> Notify Customer (unless self-notification)
            if (!ticket.getCreator().getId().equals(current.userId())) {
                notificationApplicationService.notifySupportReply(
                        current.tenantId(),
                        ticket.getCreator().getId(),
                        current.userId(),
                        ticket.getId()
                );
            }
        } else {
            // Customer replied -> Notify Assigned Moderator or all Moderators/Admins/SuperAdmins
            if (ticket.getAssignedTo() != null) {
                if (!ticket.getAssignedTo().getId().equals(current.userId())) {
                    notificationApplicationService.notifyCustomerReply(
                            current.tenantId(),
                            ticket.getAssignedTo().getId(),
                            current.userId(),
                            ticket.getId()
                    );
                }
            } else {
                java.util.List<User> moderators = userRepository.findUsersByTenantIdAndRoles(
                        current.tenantId(),
                        java.util.List.of(RoleType.MODERATOR, RoleType.TENANT_ADMIN, RoleType.PLATFORM_ADMIN)
                );
                for (User moderator : moderators) {
                    if (!moderator.getId().equals(current.userId())) {
                        notificationApplicationService.notifyCustomerReply(
                                current.tenantId(),
                                moderator.getId(),
                                current.userId(),
                                ticket.getId()
                        );
                    }
                }
            }
        }
    }

    private Ticket loadAndValidateTicket(Long ticketId, CurrentUser current) {
        // Retrieve ticket. To prevent tenant leakage, look up by ID and Tenant ID.
        Ticket ticket = ticketRepository.findByIdAndTenant_IdAndDeletedFalse(ticketId, current.tenantId())
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + ticketId));

        // Check user access
        if (!elevated(current.details())) {
            // Customer can only view/send in their own tickets
            if (!ticket.getCreator().getId().equals(current.userId())) {
                throw new AccessDeniedException("You do not have access to this ticket");
            }
        }

        return ticket;
    }

    private CurrentUser resolveCurrentUser() {
        Long tenantId = tenantResolver.resolveTenantId();
        CustomUserDetails details = authenticationFacade.getCurrentUserDetails();
        if (tenantId == null || details == null) {
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        if (!tenantId.equals(details.getTenantId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        // Throw EntityNotFoundException instead of TicketNotFoundException when the user is not found
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
        // Authoritative tenant membership check via UserRole — do NOT use user.getTenant()
        List<UserRole> activeRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(user.getId(), tenantId);
        if (activeRoles.isEmpty()) {
            throw new AccessDeniedException("User does not have active membership in the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user, details);
    }

    private boolean elevated(CustomUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_TENANT_ADMIN")
                        || authority.equals("ROLE_PLATFORM_ADMIN")
                        || authority.equals("ROLE_MODERATOR")
                        || authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN"));
    }

    private record CurrentUser(Long userId, Long tenantId, User user, CustomUserDetails details) {
    }
}

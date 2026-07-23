package com.forumx.support.ticket.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.security.facade.AuthenticationFacade;
import com.forumx.security.model.CustomUserDetails;
import com.forumx.support.ticket.dto.request.AssignTicketRequest;
import com.forumx.support.ticket.dto.request.CreateTicketRequest;
import com.forumx.support.ticket.dto.request.UpdateTicketStatusRequest;
import com.forumx.support.ticket.dto.response.TicketResponse;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.entity.TicketStatus;
import com.forumx.support.ticket.mapper.TicketMapper;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.support.ticket.service.TicketService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import com.forumx.tenant.resolver.TenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.auth.enums.RoleType;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TicketMapper ticketMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;
    private final NotificationApplicationService notificationApplicationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.forumx.support.chat.service.ChatSessionService chatSessionService;
    private final com.forumx.support.chat.repository.ChatSessionRepository chatSessionRepository;

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        CurrentUser current = resolveCurrentUser();
        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(current.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + current.tenantId()));

        Ticket ticket = ticketMapper.toEntity(request);
        ticket.setTenant(tenant);
        ticket.setCreator(current.user());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(request.getPriority() == null ? TicketPriority.MEDIUM : request.getPriority());
        ticket.setAssignedTo(null);
        // BaseEntity's audit field is separate from the ticket creator relationship.
        ticket.setCreatedBy(String.valueOf(current.userId()));

        Ticket savedTicket = ticketRepository.save(ticket);

        // Initialize ChatSession and register creator as participant for live support room
        chatSessionService.getOrCreateSession(savedTicket, current.tenantId());

        // Find moderators, admins, and super admins to notify (excluding creator self-notification)
        java.util.List<User> moderators = userRepository.findUsersByTenantIdAndRoles(
                current.tenantId(),
                java.util.List.of(RoleType.MODERATOR, RoleType.ADMIN, RoleType.SUPER_ADMIN)
        );

        for (User moderator : moderators) {
            if (!moderator.getId().equals(current.userId())) {
                notificationApplicationService.notifyTicketCreated(
                        current.tenantId(),
                        moderator.getId(),
                        current.userId(),
                        savedTicket.getId()
                );
            }
        }

        // Publish SupportTicketQueuedEvent for Live Support Queue
        eventPublisher.publishEvent(new com.forumx.support.queue.event.SupportTicketQueuedEvent(
                savedTicket.getId(),
                current.tenantId(),
                current.userId(),
                current.user().getUsername(),
                savedTicket.getSubject(),
                savedTicket.getPriority().name(),
                savedTicket.getStatus().name(),
                java.time.Instant.now()
        ));

        return ticketMapper.toResponse(savedTicket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long ticketId) {
        CurrentUser current = resolveCurrentUser();
        Optional<Ticket> ticket = elevated(current.details())
                ? ticketRepository.findByIdAndTenant_IdAndDeletedFalse(ticketId, current.tenantId())
                : ticketRepository.findByIdAndCreator_IdAndTenant_IdAndDeletedFalse(ticketId, current.userId(), current.tenantId());
        return ticketMapper.toResponse(ticket.orElseThrow(() -> new EntityNotFoundException(
                "Ticket not found with ID: " + ticketId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(Pageable pageable) {
        CurrentUser current = resolveCurrentUser();
        Page<Ticket> tickets = elevated(current.details())
                ? ticketRepository.findByTenant_IdAndDeletedFalse(current.tenantId(), pageable)
                : ticketRepository.findByCreator_IdAndTenant_IdAndDeletedFalse(current.userId(), current.tenantId(), pageable);
        return tickets.map(ticketMapper::toResponse);
    }

    @Override
    @Transactional
    public TicketResponse updateStatus(Long ticketId, UpdateTicketStatusRequest request) {
        CurrentUser current = requireElevatedUser();
        Ticket ticket = loadTenantTicket(ticketId, current.tenantId());
        String oldStatus = ticket.getStatus().name();
        ticket.setStatus(request.getStatus());
        LocalDateTime now = LocalDateTime.now();
        if (request.getStatus() == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
        } else if (request.getStatus() == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
        }
        Ticket saved = ticketRepository.save(ticket);
        if (request.getStatus() == TicketStatus.RESOLVED || request.getStatus() == TicketStatus.CLOSED) {
            chatSessionService.closeSessionByTicketId(ticketId, current.tenantId());
        }

        // Publish SupportTicketStatusChangedEvent for Live Support Queue
        eventPublisher.publishEvent(new com.forumx.support.queue.event.SupportTicketStatusChangedEvent(
                saved.getId(),
                current.tenantId(),
                oldStatus,
                saved.getStatus().name(),
                current.userId(),
                java.time.Instant.now()
        ));

        return ticketMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TicketResponse assignTicket(Long ticketId, AssignTicketRequest request) {
        CurrentUser current = requireElevatedUser();
        Ticket ticket = loadTenantTicket(ticketId, current.tenantId());
        User assignee = userRepository.findByIdAndDeletedFalse(request.getAssignedToUserId())
                .orElseThrow(() -> new EntityNotFoundException("Assigned user not found with ID: " + request.getAssignedToUserId()));
        if (assignee.getTenant() == null || !current.tenantId().equals(assignee.getTenant().getId())) {
            throw new AccessDeniedException("Assigned user does not belong to the current tenant");
        }
        ticket.setAssignedTo(assignee);
        Ticket saved = ticketRepository.save(ticket);

        // Update ChatSession lead moderator for reference
        chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(saved.getId(), current.tenantId())
                .ifPresent(session -> {
                    session.setModerator(assignee);
                    session.setStatus(com.forumx.support.chat.entity.ChatSessionStatus.ACTIVE);
                    chatSessionRepository.save(session);
                });

        // Publish SupportTicketClaimedEvent for Live Support Queue
        eventPublisher.publishEvent(new com.forumx.support.queue.event.SupportTicketClaimedEvent(
                saved.getId(),
                current.tenantId(),
                assignee.getId(),
                assignee.getUsername(),
                java.time.Instant.now()
        ));

        return ticketMapper.toResponse(saved);
    }


    private Ticket loadTenantTicket(Long ticketId, Long tenantId) {
        return ticketRepository.findByIdAndTenant_IdAndDeletedFalse(ticketId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found with ID: " + ticketId));
    }

    private CurrentUser requireElevatedUser() {
        CurrentUser current = resolveCurrentUser();
        if (!elevated(current.details())) {
            throw new AccessDeniedException("Only moderators and administrators may perform this operation");
        }
        return current;
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
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));
        if (user.getTenant() == null || !tenantId.equals(user.getTenant().getId())) {
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user, details);
    }

    private boolean elevated(CustomUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN")
                        || authority.equals("ROLE_MODERATOR"));
    }

    private record CurrentUser(Long userId, Long tenantId, User user, CustomUserDetails details) {
    }
}

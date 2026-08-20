package com.forumx.support.ticket.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.User;
import com.forumx.auth.repository.UserRepository;
import com.forumx.auth.repository.UserRoleRepository;
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
    private final UserRoleRepository userRoleRepository;
    private final TenantRepository tenantRepository;
    private final TicketMapper ticketMapper;
    private final AuthenticationFacade authenticationFacade;
    private final TenantResolver tenantResolver;
    private final NotificationApplicationService notificationApplicationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.forumx.support.chat.service.ChatSessionService chatSessionService;
    private final com.forumx.support.chat.repository.ChatSessionRepository chatSessionRepository;
    private final com.forumx.presence.service.PresenceService presenceService;
    private final com.forumx.support.ticket.policy.TicketStatusTransitionPolicy transitionPolicy;

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
        List<User> moderators = userRepository.findUsersByTenantIdAndRoles(
                current.tenantId(),
                List.of(RoleType.MODERATOR, RoleType.TENANT_ADMIN)
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
    public Page<TicketResponse> getMyTickets(TicketStatus status, Pageable pageable) {
        CurrentUser current = resolveCurrentUser();
        return elevated(current.details())
                ? (status != null
                ? ticketRepository.findByTenant_IdAndStatusAndDeletedFalse(current.tenantId(), status, pageable)
                : ticketRepository.findByTenant_IdAndDeletedFalse(current.tenantId(), pageable))
                .map(ticketMapper::toResponse)
                : (status != null
                ? ticketRepository.findByCreator_IdAndTenant_IdAndStatusAndDeletedFalse(current.userId(), current.tenantId(), status, pageable)
                : ticketRepository.findByCreator_IdAndTenant_IdAndDeletedFalse(current.userId(), current.tenantId(), pageable))
                .map(ticketMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public com.forumx.support.ticket.dto.response.SupportDashboardSummary getDashboardSummary() {
        CurrentUser current = requireElevatedUser();
        Long tenantId = current.tenantId();

        long waiting = ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(tenantId, TicketStatus.OPEN);
        long inProgress = ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(tenantId, TicketStatus.IN_PROGRESS);
        long open = waiting + inProgress;
        long closed = ticketRepository.countByTenant_IdAndStatusAndDeletedFalse(tenantId, TicketStatus.CLOSED);

        // Use explicit UTC to avoid JVM-timezone-dependent day boundaries
        LocalDateTime startOfDay = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay();
        long resolvedToday = ticketRepository.countByTenant_IdAndResolvedAtGreaterThanEqualAndDeletedFalse(tenantId, startOfDay);

        List<User> moderators = userRepository.findUsersByTenantIdAndRoles(
                tenantId,
                List.of(RoleType.MODERATOR, RoleType.TENANT_ADMIN)
        );
        long onlineModerators = moderators.stream()
                .map(User::getId)
                .distinct()
                .filter(modId -> presenceService.isUserOnlineInTenant(tenantId, modId))
                .count();

        return new com.forumx.support.ticket.dto.response.SupportDashboardSummary(
                waiting,
                open,
                closed,
                resolvedToday,
                onlineModerators
        );
    }

    @Override
    @Transactional
    public TicketResponse updateStatus(Long ticketId, UpdateTicketStatusRequest request) {
        CurrentUser current = resolveCurrentUser();
        Ticket ticket = loadTenantTicket(ticketId, current.tenantId());
        boolean isElevated = elevated(current.details());
        boolean isCreator = ticket.getCreator().getId().equals(current.userId());

        if (!isElevated && !isCreator) {
            throw new AccessDeniedException("You are not authorized to update this ticket");
        }

        TicketStatus targetStatus = request.getStatus();
        transitionPolicy.validateTransition(ticket.getStatus(), targetStatus);

        String oldStatus = ticket.getStatus().name();
        ticket.setStatus(targetStatus);
        LocalDateTime now = LocalDateTime.now();
        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
            chatSessionService.updateSessionStatusByTicket(ticketId, current.tenantId(), com.forumx.support.chat.entity.ChatSessionStatus.RESOLVED);
        } else if (targetStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
            chatSessionService.updateSessionStatusByTicket(ticketId, current.tenantId(), com.forumx.support.chat.entity.ChatSessionStatus.CLOSED);
        } else if (targetStatus == TicketStatus.REOPENED) {
            com.forumx.support.chat.entity.ChatSessionStatus reopenedChatStatus = (ticket.getAssignedTo() != null)
                    ? com.forumx.support.chat.entity.ChatSessionStatus.ACTIVE
                    : com.forumx.support.chat.entity.ChatSessionStatus.WAITING;
            chatSessionService.updateSessionStatusByTicket(ticketId, current.tenantId(), reopenedChatStatus);
        }
        Ticket saved = ticketRepository.save(ticket);

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
        List<com.forumx.auth.entity.UserRole> assigneeRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(assignee.getId(), current.tenantId());
        if (assigneeRoles.isEmpty()) {
            throw new AccessDeniedException("Assigned user does not belong to the current tenant");
        }
        ticket.setAssignedTo(assignee);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        Ticket saved = ticketRepository.save(ticket);

        // Update ChatSession lead moderator and auto-join assignee as participant (bypassing online check for system assignment)
        chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(saved.getId(), current.tenantId())
                .ifPresent(session -> {
                    session.setModerator(assignee);
                    chatSessionRepository.save(session);
                });

        chatSessionService.joinRoom(saved.getId(), assignee, com.forumx.support.chat.entity.ParticipantRole.LEAD_MODERATOR, true);

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
        log.info("[TICKET_AUTH] resolveCurrentUser: resolvedTenantId={}, detailsTenantId={}, userId={}, username={}",
                tenantId, (details != null ? details.getTenantId() : null),
                (details != null ? details.getUserId() : null),
                (details != null ? details.getUsername() : null));

        if (tenantId == null || details == null || details.getTenantId() == null) {
            log.warn("[TICKET_AUTH] Missing tenant context: resolvedTenantId={}, details={}", tenantId, details);
            throw new AccessDeniedException("Authenticated tenant context is required");
        }
        if (!tenantId.equals(details.getTenantId())) {
            log.warn("[TICKET_AUTH] Tenant mismatch: resolvedTenantId={}, detailsTenantId={}", tenantId, details.getTenantId());
            throw new AccessDeniedException("User does not belong to the current tenant");
        }
        User user = userRepository.findByIdAndDeletedFalse(details.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + details.getUserId()));

        // Authoritative tenant membership check: user must have active roles in the current tenant
        List<com.forumx.auth.entity.UserRole> activeRoles = userRoleRepository.findActiveRolesByUserIdAndTenantId(user.getId(), tenantId);
        log.info("[TICKET_AUTH] activeRoles found for userId={} in tenantId={}: count={}",
                user.getId(), tenantId, activeRoles.size());
        if (activeRoles.isEmpty()) {
            log.warn("[TICKET_AUTH] No active UserRole found for userId={} in tenantId={}", user.getId(), tenantId);
            throw new AccessDeniedException("User does not have active membership in the current tenant");
        }
        return new CurrentUser(details.getUserId(), tenantId, user, details);
    }

    private boolean elevated(CustomUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_TENANT_ADMIN")
                        || authority.equals("ROLE_MODERATOR")
                        || authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN"));
    }

    private record CurrentUser(Long userId, Long tenantId, User user, CustomUserDetails details) {
    }
}

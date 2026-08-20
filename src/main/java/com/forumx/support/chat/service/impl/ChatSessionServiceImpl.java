package com.forumx.support.chat.service.impl;

import com.forumx.auth.entity.User;
import com.forumx.presence.service.PresenceService;
import com.forumx.support.chat.dto.response.ParticipantResponse;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.chat.event.ephemeral.ChatParticipantJoinedEvent;
import com.forumx.support.chat.event.ephemeral.ChatParticipantLeftEvent;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.repository.SupportSessionParticipantRepository;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.repository.TicketRepository;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final SupportSessionParticipantRepository participantRepository;
    private final TicketRepository ticketRepository;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ChatSession getOrCreateSession(Ticket ticket, Long tenantId) {
        return chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticket.getId(), tenantId)
                .orElseGet(() -> {
                    // ticket.getTenant() is always the authoritative tenant. Do NOT fall back to creator's home tenant.
                    Tenant tenant = ticket.getTenant();
                    if (tenant == null) {
                        throw new IllegalStateException(
                                "Ticket ID=" + ticket.getId() + " has no tenant assigned. Cannot create chat session.");
                    }
                    ChatSession session = ChatSession.builder()
                            .ticket(ticket)
                            .tenant(tenant)
                            .customer(ticket.getCreator())
                            .moderator(ticket.getAssignedTo())
                            .status(ChatSessionStatus.WAITING)
                            .build();
                    log.info("Creating new chat session for ticketId={}", ticket.getId());
                    ChatSession savedSession = chatSessionRepository.save(session);

                    // Automatically register ticket creator as initial CUSTOMER participant
                    if (ticket.getCreator() != null) {
                        SupportSessionParticipant customerParticipant = SupportSessionParticipant.builder()
                                .session(savedSession)
                                .tenant(savedSession.getTenant())
                                .user(ticket.getCreator())
                                .role(ParticipantRole.CUSTOMER)
                                .joinedAt(Instant.now())
                                .isActive(true)
                                .build();
                        participantRepository.save(customerParticipant);
                    }

                    return savedSession;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSession getSessionByTicketId(Long ticketId, Long tenantId) {
        return chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticketId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found for ticket ID: " + ticketId));
    }

    @Override
    @Transactional
    public void closeSessionByTicketId(Long ticketId, Long tenantId) {
        updateSessionStatusByTicket(ticketId, tenantId, ChatSessionStatus.CLOSED);
    }

    @Override
    @Transactional
    public void updateSessionStatusByTicket(Long ticketId, Long tenantId, ChatSessionStatus status) {
        chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticketId, tenantId)
                .ifPresentOrElse(session -> {
                    if (session.getStatus() != status) {
                        session.setStatus(status);
                        chatSessionRepository.save(session);
                        log.info("Updated chat session ID={} to status={} for ticketId={}", session.getId(), status, ticketId);
                    }
                }, () -> log.debug("No chat session found for ticketId={} to update status.", ticketId));
    }

    @Override
    @Transactional
    public SupportSessionParticipant joinRoom(Long ticketId, User user, ParticipantRole role) {
        return joinRoom(ticketId, user, role, true);
    }

    @Override
    @Transactional
    public SupportSessionParticipant joinRoom(Long ticketId, User user, ParticipantRole role, boolean bypassOnlineCheck) {
        log.info("[DIAGNOSTIC] ChatSessionServiceImpl.joinRoom START - TicketId: {}, User: {}, UserId: {}, Role: {}",
                ticketId, user.getUsername(), user.getId(), role);

        // Derive tenantId from the ticket (authoritative source), NOT from user.getTenant()
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found with ID: " + ticketId));

        Long tenantId = ticket.getTenant() != null ? ticket.getTenant().getId() : null;
        if (tenantId == null) {
            throw new AccessDeniedException("Ticket has no tenant context — cannot join support room");
        }

        // Verify the ticket belongs to the expected tenant by re-loading with tenant scope
        ticket = ticketRepository.findByIdAndTenant_IdAndDeletedFalse(ticketId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found with ID: " + ticketId));

        ChatSession session = getOrCreateSession(ticket, tenantId);

        Optional<SupportSessionParticipant> existingActive = participantRepository
                .findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(ticketId, user.getId());

        if (existingActive.isPresent()) {
            SupportSessionParticipant activeParticipant = existingActive.get();
            log.info("[DIAGNOSTIC] joinRoom CASE C (Already Active) - ParticipantId: {}, Role: {}",
                    activeParticipant.getId(), activeParticipant.getRole());
            if (role == ParticipantRole.LEAD_MODERATOR && activeParticipant.getRole() != ParticipantRole.LEAD_MODERATOR) {
                activeParticipant.setRole(ParticipantRole.LEAD_MODERATOR);
                participantRepository.save(activeParticipant);
            }
            if (role != ParticipantRole.CUSTOMER && session.getStatus() == ChatSessionStatus.WAITING) {
                session.setStatus(ChatSessionStatus.ACTIVE);
                chatSessionRepository.save(session);
            }
            return activeParticipant;
        }

        // Record a distinct active participation window (joinedAt -> leftAt)
        Instant now = Instant.now();
        // effectiveTenant comes from the chat session (which was created from ticket.getTenant())
        Tenant effectiveTenant = session.getTenant();

        SupportSessionParticipant participant = SupportSessionParticipant.builder()
                .session(session)
                .tenant(effectiveTenant)
                .user(user)
                .role(role)
                .joinedAt(now)
                .isActive(true)
                .build();
        log.info("[DIAGNOSTIC] joinRoom Creating new participation window record at joinedAt={}", now);

        SupportSessionParticipant saved = participantRepository.save(participant);

        if (role != ParticipantRole.CUSTOMER && session.getStatus() == ChatSessionStatus.WAITING) {
            session.setStatus(ChatSessionStatus.ACTIVE);
            chatSessionRepository.save(session);
        }

        eventPublisher.publishEvent(new ChatParticipantJoinedEvent(
                ticketId,
                session.getId(),
                session.getTenant() != null ? session.getTenant().getId() : tenantId,
                user.getId(),
                user.getUsername(),
                role.name(),
                now
        ));

        log.info("[DIAGNOSTIC] joinRoom SUCCESS - Saved ParticipantId: {}, IsActive: {}", saved.getId(), saved.isActive());
        return saved;
    }

    @Override
    @Transactional
    public SupportSessionParticipant leaveRoom(Long ticketId, User user) {
        SupportSessionParticipant participant = participantRepository
                .findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(ticketId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Active room participant not found for user ID: " + user.getId()));

        Instant now = Instant.now();
        participant.setActive(false);
        participant.setLeftAt(now);
        SupportSessionParticipant saved = participantRepository.save(participant);

        eventPublisher.publishEvent(new ChatParticipantLeftEvent(
                ticketId,
                participant.getSession().getId(),
                participant.getTenant() != null ? participant.getTenant().getId() : 1L,
                user.getId(),
                user.getUsername(),
                participant.getRole().name(),
                now
        ));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipants(Long ticketId, Long tenantId) {
        List<SupportSessionParticipant> activeParticipants = participantRepository
                .findBySession_Ticket_IdAndIsActiveTrue(ticketId);

        log.info("[DIAGNOSTIC] ChatSessionServiceImpl.getParticipants - TicketId: {}, TenantId: {}, Total active found: {}",
                ticketId, tenantId, activeParticipants.size());

        return activeParticipants.stream()
                .filter(p -> tenantId == null || p.getTenant() == null || p.getTenant().getId().equals(tenantId))
                .map(p -> ParticipantResponse.builder()
                        .id(p.getId())
                        .ticketId(ticketId)
                        .sessionId(p.getSession().getId())
                        .userId(p.getUser().getId())
                        .username(p.getUser().getUsername())
                        .role(p.getRole().name())
                        .joinedAt(p.getJoinedAt())
                        .leftAt(p.getLeftAt())
                        .isActive(p.isActive())
                        .isOnline(presenceService.isUserOnlineInTenant(tenantId, p.getUser().getId()))
                        .build())
                .toList();
    }

    /**
     * Terminates all active support-room participations for a given user.
     * Called by TenantStaffService when a moderator account is disabled.
     * <p>
     * For each active participation: sets isActive=false, leftAt=now,
     * and publishes a ChatParticipantLeftEvent so connected clients receive
     * the participant-left WebSocket notification.
     * <p>
     * Does NOT close tickets or chat sessions.
     * Does NOT delete participation records — history is preserved.
     *
     * @param userId the ID of the user whose active participations should be terminated
     * @return the number of participations terminated
     */
    @Override
    @Transactional
    public int terminateActiveParticipations(Long userId) {
        List<SupportSessionParticipant> active = participantRepository.findAllByUser_IdAndIsActiveTrue(userId);
        if (active.isEmpty()) {
            log.debug("terminateActiveParticipations: no active participations for userId={}", userId);
            return 0;
        }

        Instant now = Instant.now();
        for (SupportSessionParticipant p : active) {
            p.setActive(false);
            p.setLeftAt(now);
            participantRepository.save(p);

            try {
                eventPublisher.publishEvent(new ChatParticipantLeftEvent(
                        p.getSession().getTicket() != null ? p.getSession().getTicket().getId() : null,
                        p.getSession().getId(),
                        p.getTenant() != null ? p.getTenant().getId() : null,
                        p.getUser().getId(),
                        p.getUser().getUsername(),
                        p.getRole().name(),
                        now
                ));
            } catch (Exception e) {
                // Event publishing failure must not prevent the disable operation from completing
                log.warn("Failed to publish ChatParticipantLeftEvent for participantId={}, userId={}: {}",
                        p.getId(), userId, e.getMessage());
            }
        }

        log.info("terminateActiveParticipations: terminated {} active participations for userId={}", active.size(), userId);
        return active.size();
    }
}


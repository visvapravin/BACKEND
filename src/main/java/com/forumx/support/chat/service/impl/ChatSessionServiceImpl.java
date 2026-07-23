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
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final SupportSessionParticipantRepository participantRepository;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ChatSession getOrCreateSession(Ticket ticket, Long tenantId) {
        return chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticket.getId(), tenantId)
                .orElseGet(() -> {
                    ChatSession session = ChatSession.builder()
                            .ticket(ticket)
                            .tenant(ticket.getTenant())
                            .customer(ticket.getCreator())
                            .moderator(ticket.getAssignedTo())
                            .status(ChatSessionStatus.ACTIVE)
                            .build();
                    log.info("Creating new chat session for ticketId={}", ticket.getId());
                    ChatSession savedSession = chatSessionRepository.save(session);

                    // Automatically register ticket creator as initial CUSTOMER participant
                    SupportSessionParticipant customerParticipant = SupportSessionParticipant.builder()
                            .session(savedSession)
                            .tenant(savedSession.getTenant())
                            .user(ticket.getCreator())
                            .role(ParticipantRole.CUSTOMER)
                            .joinedAt(Instant.now())
                            .isActive(true)
                            .build();
                    participantRepository.save(customerParticipant);

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
        chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticketId, tenantId)
                .ifPresentOrElse(session -> {
                    if (session.getStatus() != ChatSessionStatus.CLOSED) {
                        session.setStatus(ChatSessionStatus.CLOSED);
                        chatSessionRepository.save(session);
                        log.info("Closed chat session ID={} for ticketId={}", session.getId(), ticketId);
                    }
                }, () -> log.debug("No active chat session found for ticketId={} to close.", ticketId));
    }

    @Override
    @Transactional
    public SupportSessionParticipant joinRoom(Long ticketId, User user, ParticipantRole role) {
        if (role != ParticipantRole.CUSTOMER && !presenceService.isOnline(user.getId())) {
            throw new IllegalStateException("User must be online to join support room");
        }

        ChatSession session = getOrCreateSession(user.getTenant() != null ? 
                user.getTenant().getId() : null, ticketId);
        
        Optional<SupportSessionParticipant> existingActive = participantRepository
                .findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(ticketId, user.getId());
        
        if (existingActive.isPresent()) {
            SupportSessionParticipant activeParticipant = existingActive.get();
            if (role == ParticipantRole.LEAD_MODERATOR) {
                activeParticipant.setRole(ParticipantRole.LEAD_MODERATOR);
                participantRepository.save(activeParticipant);
            }
            return activeParticipant;
        }

        // Check if there was a previous inactive participant session
        Optional<SupportSessionParticipant> previousInactive = participantRepository
                .findTopBySession_Ticket_IdAndUser_IdOrderByJoinedAtDesc(ticketId, user.getId());

        SupportSessionParticipant participant;
        Instant now = Instant.now();
        if (previousInactive.isPresent()) {
            participant = previousInactive.get();
            participant.setActive(true);
            participant.setJoinedAt(now);
            participant.setLeftAt(null);
            participant.setRole(role);
        } else {
            participant = SupportSessionParticipant.builder()
                    .session(session)
                    .tenant(session.getTenant())
                    .user(user)
                    .role(role)
                    .joinedAt(now)
                    .isActive(true)
                    .build();
        }

        SupportSessionParticipant saved = participantRepository.save(participant);

        eventPublisher.publishEvent(new ChatParticipantJoinedEvent(
                ticketId,
                session.getId(),
                session.getTenant().getId(),
                user.getId(),
                user.getUsername(),
                role.name(),
                now
        ));

        return saved;
    }

    private ChatSession getOrCreateSession(Long tenantId, Long ticketId) {
        return chatSessionRepository.findByTicket_IdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket/ChatSession not found with ID: " + ticketId));
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
                participant.getTenant().getId(),
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
                .findBySession_Ticket_IdAndTenant_IdAndIsActiveTrue(ticketId, tenantId);

        return activeParticipants.stream()
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
                        .isOnline(presenceService.isOnline(p.getUser().getId()))
                        .build())
                .toList();
    }
}

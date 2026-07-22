package com.forumx.support.chat.service.impl;

import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.repository.ChatSessionRepository;
import com.forumx.support.chat.service.ChatSessionService;
import com.forumx.support.ticket.entity.Ticket;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    @Override
    @Transactional
    public ChatSession getOrCreateSession(Ticket ticket, Long tenantId) {
        return chatSessionRepository.findByTicket_IdAndTenant_IdAndDeletedFalse(ticket.getId(), tenantId)
                .orElseGet(() -> {
                    if (ticket.getAssignedTo() == null) {
                        throw new IllegalStateException("Cannot create chat session. Support ticket is not assigned to any moderator.");
                    }
                    ChatSession session = ChatSession.builder()
                            .ticket(ticket)
                            .tenant(ticket.getTenant())
                            .customer(ticket.getCreator())
                            .moderator(ticket.getAssignedTo())
                            .status(ChatSessionStatus.ACTIVE)
                            .build();
                    log.info("Creating new chat session for ticketId={}", ticket.getId());
                    return chatSessionRepository.save(session);
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
}

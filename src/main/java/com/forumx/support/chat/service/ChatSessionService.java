package com.forumx.support.chat.service;

import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.ticket.entity.Ticket;

public interface ChatSessionService {
    ChatSession getOrCreateSession(Ticket ticket, Long tenantId);
    ChatSession getSessionByTicketId(Long ticketId, Long tenantId);
    void closeSessionByTicketId(Long ticketId, Long tenantId);
}

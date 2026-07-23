package com.forumx.support.chat.service;

import com.forumx.auth.entity.User;
import com.forumx.support.chat.dto.response.ParticipantResponse;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.ticket.entity.Ticket;
import java.util.List;

public interface ChatSessionService {
    ChatSession getOrCreateSession(Ticket ticket, Long tenantId);
    ChatSession getSessionByTicketId(Long ticketId, Long tenantId);
    void closeSessionByTicketId(Long ticketId, Long tenantId);

    SupportSessionParticipant joinRoom(Long ticketId, User user, ParticipantRole role);
    SupportSessionParticipant leaveRoom(Long ticketId, User user);
    List<ParticipantResponse> getParticipants(Long ticketId, Long tenantId);
}

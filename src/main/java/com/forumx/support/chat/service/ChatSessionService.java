package com.forumx.support.chat.service;

import com.forumx.auth.entity.User;
import com.forumx.support.chat.dto.response.ParticipantResponse;
import com.forumx.support.chat.entity.ChatSession;
import com.forumx.support.chat.entity.ChatSessionStatus;
import com.forumx.support.chat.entity.ParticipantRole;
import com.forumx.support.chat.entity.SupportSessionParticipant;
import com.forumx.support.ticket.entity.Ticket;
import java.util.List;

public interface ChatSessionService {
    ChatSession getOrCreateSession(Ticket ticket, Long tenantId);
    ChatSession getSessionByTicketId(Long ticketId, Long tenantId);
    void closeSessionByTicketId(Long ticketId, Long tenantId);
    void updateSessionStatusByTicket(Long ticketId, Long tenantId, ChatSessionStatus status);

    SupportSessionParticipant joinRoom(Long ticketId, User user, ParticipantRole role);
    SupportSessionParticipant joinRoom(Long ticketId, User user, ParticipantRole role, boolean bypassOnlineCheck);
    SupportSessionParticipant leaveRoom(Long ticketId, User user);
    List<ParticipantResponse> getParticipants(Long ticketId, Long tenantId);

    /**
     * Terminates all active support-room participations for a given user.
     * Called by TenantStaffService when disabling a moderator account.
     * Sets isActive=false, leftAt=now for each active participation record
     * and publishes ChatParticipantLeftEvent per participation.
     * Does NOT close tickets or chat sessions.
     * Does NOT delete participation history.
     *
     * @param userId the ID of the user whose active participations should be terminated
     * @return the number of participations terminated
     */
    int terminateActiveParticipations(Long userId);
}



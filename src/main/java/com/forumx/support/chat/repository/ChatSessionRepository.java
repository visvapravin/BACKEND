package com.forumx.support.chat.repository;

import com.forumx.support.chat.entity.ChatSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByTicket_IdAndDeletedFalse(Long ticketId);
    Optional<ChatSession> findByTicket_IdAndTenant_IdAndDeletedFalse(Long ticketId, Long tenantId);
}

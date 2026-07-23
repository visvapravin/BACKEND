package com.forumx.support.chat.repository;

import com.forumx.support.chat.entity.SupportSessionParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportSessionParticipantRepository extends JpaRepository<SupportSessionParticipant, Long> {

    Optional<SupportSessionParticipant> findBySession_IdAndUser_IdAndIsActiveTrue(Long sessionId, Long userId);

    Optional<SupportSessionParticipant> findBySession_Ticket_IdAndUser_IdAndIsActiveTrue(Long ticketId, Long userId);

    List<SupportSessionParticipant> findBySession_IdAndIsActiveTrue(Long sessionId);

    List<SupportSessionParticipant> findBySession_Ticket_IdAndIsActiveTrue(Long ticketId);

    List<SupportSessionParticipant> findBySession_Ticket_IdAndTenant_IdAndIsActiveTrue(Long ticketId, Long tenantId);

    Optional<SupportSessionParticipant> findTopBySession_Ticket_IdAndUser_IdOrderByJoinedAtDesc(Long ticketId, Long userId);

    boolean existsBySession_Ticket_IdAndUser_IdAndIsActiveTrue(Long ticketId, Long userId);
}

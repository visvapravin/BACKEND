package com.forumx.support.ticket.repository;

import java.util.Optional;
import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    Page<TicketMessage> findByTicketAndDeletedFalse(Ticket ticket, Pageable pageable);

    Optional<TicketMessage> findByIdAndDeletedFalse(Long id);

    boolean existsByIdAndDeletedFalse(Long id);
}

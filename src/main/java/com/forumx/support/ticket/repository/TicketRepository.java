package com.forumx.support.ticket.repository;

import java.util.Optional;

import com.forumx.support.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    Page<Ticket> findByCreator_IdAndTenant_IdAndDeletedFalse(Long creatorId, Long tenantId, Pageable pageable);

    Optional<Ticket> findByIdAndTenant_IdAndDeletedFalse(Long id, Long tenantId);

    Optional<Ticket> findByIdAndCreator_IdAndTenant_IdAndDeletedFalse(Long id, Long creatorId, Long tenantId);

    Page<Ticket> findByAssignedTo_IdAndTenant_IdAndDeletedFalse(Long assignedToId, Long tenantId, Pageable pageable);
}

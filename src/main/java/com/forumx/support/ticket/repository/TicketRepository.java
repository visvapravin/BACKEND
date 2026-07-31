package com.forumx.support.ticket.repository;

import java.util.Optional;

import com.forumx.support.ticket.entity.Ticket;
import com.forumx.support.ticket.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findByTenant_IdAndDeletedFalse(Long tenantId, Pageable pageable);

    Page<Ticket> findByCreator_IdAndTenant_IdAndDeletedFalse(Long creatorId, Long tenantId, Pageable pageable);

    Optional<Ticket> findByIdAndTenant_IdAndDeletedFalse(Long id, Long tenantId);

    Optional<Ticket> findByIdAndCreator_IdAndTenant_IdAndDeletedFalse(Long id, Long creatorId, Long tenantId);

    Page<Ticket> findByAssignedTo_IdAndTenant_IdAndDeletedFalse(Long assignedToId, Long tenantId, Pageable pageable);

    Page<Ticket> findByTenant_IdAndStatusAndDeletedFalse(Long tenantId, TicketStatus status, Pageable pageable);

    Page<Ticket> findByCreator_IdAndTenant_IdAndStatusAndDeletedFalse(Long creatorId, Long tenantId, TicketStatus status, Pageable pageable);

    long countByTenant_IdAndStatusAndDeletedFalse(Long tenantId, TicketStatus status);

    long countByTenant_IdAndStatusInAndDeletedFalse(Long tenantId, java.util.Collection<TicketStatus> statuses);

    long countByTenant_IdAndResolvedAtGreaterThanEqualAndDeletedFalse(Long tenantId, java.time.LocalDateTime dateTime);

    long countByTenant_IdAndResolvedAtGreaterThanEqualAndResolvedAtLessThanAndDeletedFalse(Long tenantId, java.time.LocalDateTime startDateTime, java.time.LocalDateTime endDateTime);
}


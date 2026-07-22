package com.forumx.support.ticket.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;

import com.forumx.support.ticket.entity.TicketPriority;
import com.forumx.support.ticket.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private String subject;
    private String description;
    private TicketStatus status;
    private TicketPriority priority;
    private Long createdBy;
    private Long assignedTo;
    private Instant createdAt;
    private Instant updatedAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
